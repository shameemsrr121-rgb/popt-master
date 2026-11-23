package com.example.data.repository

import com.example.data.cloud.SqlConnectionManager
import com.example.data.dao.*
import com.example.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Enterprise Repository for PO Master ERP.
 * Combines local persistence with the Central Cloud SQL Server Connection Manager
 * to handle CRUD operations safely with zero impact on existing ERP tables.
 */
class POMasterRepository(
    private val supplierDao: SupplierDao,
    private val inventoryDao: InventoryDao,
    private val poDao: PurchaseOrderDao,
    private val marginRuleDao: MarginRuleDao,
    private val locationDao: LocationDao,
    private val userDao: UserDao,
    private val companyProfileDao: CompanyProfileDao,
    private val storeHierarchyDao: StoreHierarchyDao,
    private val categoryTaxDao: CategoryTaxDao,
    private val purchasePlanNoteDao: PurchasePlanNoteDao,
    private val sqlManager: SqlConnectionManager? = null
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Purchase Planning & Travel / Note Master
    val purchasePlanNotes: Flow<List<PurchasePlanNoteEntity>> = purchasePlanNoteDao.getAllNotes()
    fun getPlanNotesBetween(startMillis: Long, endMillis: Long): Flow<List<PurchasePlanNoteEntity>> =
        purchasePlanNoteDao.getNotesBetweenDates(startMillis, endMillis)
        
    suspend fun insertPurchasePlanNote(note: PurchasePlanNoteEntity): Long {
        val id = purchasePlanNoteDao.insertNote(note)
        return id
    }
    
    suspend fun updatePurchasePlanNote(note: PurchasePlanNoteEntity) {
        purchasePlanNoteDao.updateNote(note)
    }
    
    suspend fun deletePurchasePlanNote(note: PurchasePlanNoteEntity) {
        purchasePlanNoteDao.deleteNote(note)
    }
    
    suspend fun deletePurchasePlanNoteById(id: Long) {
        purchasePlanNoteDao.deleteNoteById(id)
    }

    // Category & Tax Rules Master
    val categoryTaxRules: Flow<List<CategoryTaxEntity>> = categoryTaxDao.getAllCategoryTaxRules()
    suspend fun insertCategoryTaxRule(rule: CategoryTaxEntity): Long = categoryTaxDao.insertCategoryTaxRule(rule)
    suspend fun updateCategoryTaxRule(rule: CategoryTaxEntity) = categoryTaxDao.updateCategoryTaxRule(rule)
    suspend fun deleteCategoryTaxRule(rule: CategoryTaxEntity) = categoryTaxDao.deleteCategoryTaxRule(rule)
    suspend fun deleteCategoryTaxRuleById(id: Long) = categoryTaxDao.deleteCategoryTaxRuleById(id)

    // 0. Company Profile Master
    val companyProfile: Flow<CompanyProfileEntity?> = companyProfileDao.getCompanyProfile()
    suspend fun saveCompanyProfile(companyProfile: CompanyProfileEntity) {
        companyProfileDao.saveCompanyProfile(companyProfile)
        sqlManager?.saveCompanyProfile(companyProfile)
    }

    // Store Hierarchy Master
    val storeHierarchy: Flow<List<StoreHierarchyEntity>> = storeHierarchyDao.getAllHierarchy()
    suspend fun addHierarchyNode(division: String, section: String = "", department: String = ""): Long {
        if (division.isBlank()) return 0L
        return storeHierarchyDao.insertHierarchy(
            StoreHierarchyEntity(
                division = division.trim(),
                section = section.trim(),
                department = department.trim()
            )
        )
    }
    suspend fun deleteHierarchyNode(node: StoreHierarchyEntity) = storeHierarchyDao.deleteHierarchy(node)

    // 1. Supplier Master (Cloud SQL Integrated)
    val suppliers: Flow<List<SupplierEntity>> = supplierDao.getAllSuppliers()
    fun getSupplierById(id: Long): Flow<SupplierEntity?> = supplierDao.getSupplierById(id)
    
    suspend fun insertSupplier(supplier: SupplierEntity): Long {
        val id = supplierDao.insertSupplier(supplier)
        sqlManager?.insertSupplier(supplier.copy(id = id))
        return id
    }
    
    suspend fun updateSupplier(supplier: SupplierEntity) {
        supplierDao.updateSupplier(supplier)
        sqlManager?.updateSupplier(supplier)
    }
    
    suspend fun deleteSupplier(supplier: SupplierEntity) {
        supplierDao.deleteSupplier(supplier)
        sqlManager?.deleteSupplier(supplier.id)
    }

    // 2. Inventory Master (Cloud SQL Integrated)
    val inventoryItems: Flow<List<InventoryEntity>> = inventoryDao.getAllInventory()
    val lowStockItems: Flow<List<InventoryEntity>> = inventoryDao.getLowStockItems()
    fun getInventoryItemById(id: Long): Flow<InventoryEntity?> = inventoryDao.getInventoryById(id)
    
    suspend fun insertInventoryItem(item: InventoryEntity): Long {
        val id = inventoryDao.insertInventoryItem(item)
        sqlManager?.insertInventoryItem(item.copy(id = id))
        return id
    }
    
    suspend fun updateInventoryItem(item: InventoryEntity) {
        inventoryDao.updateInventoryItem(item)
        sqlManager?.updateInventoryItem(item)
    }
    
    suspend fun deleteInventoryItem(item: InventoryEntity) {
        inventoryDao.deleteInventoryItem(item)
        sqlManager?.deleteInventoryItem(item.id)
    }
    
    suspend fun adjustStock(itemId: Long, qty: Int) {
        inventoryDao.updateStockQuantity(itemId, qty)
        sqlManager?.adjustInventoryStock(itemId, qty)
    }

    // 3. Procurement Master (PO Tracking & Cloud SQL Transactional CRUD)
    val purchaseOrders: Flow<List<PurchaseOrderEntity>> = poDao.getAllPurchaseOrders()
    fun getPurchaseOrderById(poId: Long): Flow<PurchaseOrderEntity?> = poDao.getPurchaseOrderById(poId)
    fun getPOItems(poId: Long): Flow<List<PurchaseOrderItemEntity>> = poDao.getPOItems(poId)
    suspend fun getAllPOItemsDirect(): List<PurchaseOrderItemEntity> = poDao.getAllPOItemsDirect()
    fun getPOTaxBreakdowns(poId: Long): Flow<List<POTaxBreakdownEntity>> = poDao.getPOTaxBreakdowns(poId)
    fun getTaxBreakdownSummary(poId: Long): Flow<List<POTaxBreakdownSummary>> = poDao.getTaxBreakdownSummary(poId)

    suspend fun createPurchaseOrder(
        po: PurchaseOrderEntity,
        items: List<PurchaseOrderItemEntity>,
        taxBreakdowns: List<POTaxBreakdownEntity> = emptyList()
    ): Long {
        val poId = poDao.insertPurchaseOrder(po)
        val itemsWithPoId = items.map { it.copy(poId = poId) }
        poDao.insertPOItems(itemsWithPoId)
        
        val calculatedBreakdowns = if (taxBreakdowns.isNotEmpty()) {
            taxBreakdowns.map { it.copy(poId = poId) }
        } else {
            itemsWithPoId.groupBy { it.taxPercent }.map { (rate, lineItems) ->
                val taxable = lineItems.sumOf { it.taxableAmount }
                val tax = lineItems.sumOf { it.taxAmount }
                POTaxBreakdownEntity(
                    poId = poId,
                    gstPercent = rate,
                    taxableAmount = taxable,
                    cgstPercent = rate / 2.0,
                    cgstAmount = tax / 2.0,
                    sgstPercent = rate / 2.0,
                    sgstAmount = tax / 2.0,
                    totalTaxAmount = tax
                )
            }
        }
        poDao.insertPOTaxBreakdowns(calculatedBreakdowns)
        
        // Dispatch SQL Server Transaction
        sqlManager?.createPurchaseOrder(po.copy(id = poId), itemsWithPoId)
        return poId
    }

    suspend fun updatePurchaseOrder(po: PurchaseOrderEntity) {
        poDao.updatePurchaseOrder(po)
        sqlManager?.updatePurchaseOrder(po)
    }

    suspend fun receivePOAndStockUp(po: PurchaseOrderEntity, items: List<PurchaseOrderItemEntity>) {
        poDao.updatePurchaseOrder(po)
        sqlManager?.updatePurchaseOrder(po)

        for (item in items) {
            val oldItem = poDao.getPOItemByIdSync(item.id)
            val oldReceived = oldItem?.quantityReceived ?: 0
            val delta = item.quantityReceived - oldReceived
            
            poDao.insertPOItems(listOf(item))
            if (delta > 0) {
                inventoryDao.updateStockQuantity(item.inventoryItemId, delta)
                sqlManager?.adjustInventoryStock(item.inventoryItemId, delta)
            }
        }
    }

    suspend fun deletePurchaseOrder(po: PurchaseOrderEntity) {
        poDao.deletePOTaxBreakdownsByPoId(po.id)
        poDao.deletePOItemsByPoId(po.id)
        poDao.deletePurchaseOrder(po)
        sqlManager?.deletePurchaseOrder(po.id)
    }

    // 4. Margin Master
    val marginRules: Flow<List<MarginRuleEntity>> = marginRuleDao.getAllMarginRules()
    suspend fun insertMarginRule(rule: MarginRuleEntity): Long = marginRuleDao.insertMarginRule(rule)
    suspend fun updateMarginRule(rule: MarginRuleEntity) = marginRuleDao.updateMarginRule(rule)
    suspend fun deleteMarginRule(rule: MarginRuleEntity) = marginRuleDao.deleteMarginRule(rule)

    // 5. Location Master
    val locations: Flow<List<LocationEntity>> = locationDao.getAllLocations()
    suspend fun insertLocation(location: LocationEntity): Long = locationDao.insertLocation(location)
    suspend fun updateLocation(location: LocationEntity) = locationDao.updateLocation(location)
    suspend fun deleteLocation(location: LocationEntity) = locationDao.deleteLocation(location)

    // 6. User Rights & Account Control
    val users: Flow<List<UserAccountEntity>> = userDao.getAllUsers()
    suspend fun authenticateUser(username: String, password: String): UserAccountEntity? =
        userDao.authenticateUser(username, password)
    suspend fun getUserByUsername(username: String): UserAccountEntity? =
        userDao.getUserByUsername(username)
    suspend fun insertUser(user: UserAccountEntity): Long = userDao.insertUser(user)
    suspend fun updateUser(user: UserAccountEntity) = userDao.updateUser(user)
    suspend fun deleteUser(user: UserAccountEntity) = userDao.deleteUser(user)
}
