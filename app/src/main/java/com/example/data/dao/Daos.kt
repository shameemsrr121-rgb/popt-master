package com.example.data.dao

import androidx.room.*
import com.example.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id")
    fun getSupplierById(id: Long): Flow<SupplierEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    @Update
    suspend fun updateSupplier(supplier: SupplierEntity)

    @Delete
    suspend fun deleteSupplier(supplier: SupplierEntity)
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory ORDER BY name ASC")
    fun getAllInventory(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE id = :id")
    fun getInventoryById(id: Long): Flow<InventoryEntity?>

    @Query("SELECT * FROM inventory WHERE currentStock <= minReorderLevel")
    fun getLowStockItems(): Flow<List<InventoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInventoryItem(item: InventoryEntity): Long

    @Update
    suspend fun updateInventoryItem(item: InventoryEntity)

    @Delete
    suspend fun deleteInventoryItem(item: InventoryEntity)

    @Query("UPDATE inventory SET currentStock = currentStock + :qty WHERE id = :itemId")
    suspend fun updateStockQuantity(itemId: Long, qty: Int)
}

@Dao
interface PurchaseOrderDao {
    @Query("SELECT * FROM purchase_orders ORDER BY id DESC")
    fun getAllPurchaseOrders(): Flow<List<PurchaseOrderEntity>>

    @Query("SELECT * FROM purchase_orders ORDER BY id DESC")
    suspend fun getAllPurchaseOrdersDirect(): List<PurchaseOrderEntity>

    @Query("SELECT * FROM purchase_orders WHERE id = :poId")
    fun getPurchaseOrderById(poId: Long): Flow<PurchaseOrderEntity?>

    @Query("SELECT * FROM po_items WHERE poId = :poId")
    fun getPOItems(poId: Long): Flow<List<PurchaseOrderItemEntity>>

    @Query("SELECT * FROM po_items")
    suspend fun getAllPOItemsDirect(): List<PurchaseOrderItemEntity>

    @Query("SELECT * FROM po_items WHERE id = :itemId")
    suspend fun getPOItemByIdSync(itemId: Long): PurchaseOrderItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPurchaseOrder(po: PurchaseOrderEntity): Long

    @Update
    suspend fun updatePurchaseOrder(po: PurchaseOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPOItems(items: List<PurchaseOrderItemEntity>)

    @Update
    suspend fun updatePOItem(item: PurchaseOrderItemEntity)

    @Delete
    suspend fun deletePurchaseOrder(po: PurchaseOrderEntity)

    @Query("DELETE FROM po_items WHERE poId = :poId")
    suspend fun deletePOItemsByPoId(poId: Long)

    // Stored procedure / Grouped Aggregation Query for In-built Database Tax Breakdown
    @Query("SELECT taxPercent as gstPercent, SUM(taxableAmount) as taxableAmount, SUM(taxAmount) as taxAmount, COUNT(*) as itemCount FROM po_items WHERE poId = :poId GROUP BY taxPercent ORDER BY taxPercent ASC")
    fun getTaxBreakdownSummary(poId: Long): Flow<List<POTaxBreakdownSummary>>

    @Query("SELECT * FROM po_tax_breakdowns WHERE poId = :poId ORDER BY gstPercent ASC")
    fun getPOTaxBreakdowns(poId: Long): Flow<List<POTaxBreakdownEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPOTaxBreakdowns(breakdowns: List<POTaxBreakdownEntity>)

    @Query("DELETE FROM po_tax_breakdowns WHERE poId = :poId")
    suspend fun deletePOTaxBreakdownsByPoId(poId: Long)
}

@Dao
interface MarginRuleDao {
    @Query("SELECT * FROM margin_rules ORDER BY id ASC")
    fun getAllMarginRules(): Flow<List<MarginRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarginRule(rule: MarginRuleEntity): Long

    @Update
    suspend fun updateMarginRule(rule: MarginRuleEntity)

    @Delete
    suspend fun deleteMarginRule(rule: MarginRuleEntity)
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name ASC")
    fun getAllLocations(): Flow<List<LocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity): Long

    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Delete
    suspend fun deleteLocation(location: LocationEntity)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY fullName ASC")
    fun getAllUsers(): Flow<List<UserAccountEntity>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserAccountEntity?

    @Query("SELECT * FROM users WHERE username = :username AND passwordHash = :password LIMIT 1")
    suspend fun authenticateUser(username: String, password: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccountEntity): Long

    @Update
    suspend fun updateUser(user: UserAccountEntity)

    @Delete
    suspend fun deleteUser(user: UserAccountEntity)
}

@Dao
interface CompanyProfileDao {
    @Query("SELECT * FROM company_profile WHERE id = 1 LIMIT 1")
    fun getCompanyProfile(): Flow<CompanyProfileEntity?>

    @Query("SELECT * FROM company_profile WHERE id = 1 LIMIT 1")
    suspend fun getCompanyProfileSync(): CompanyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCompanyProfile(companyProfile: CompanyProfileEntity)
}

@Dao
interface StoreHierarchyDao {
    @Query("SELECT * FROM store_hierarchy ORDER BY division ASC, section ASC, department ASC")
    fun getAllHierarchy(): Flow<List<StoreHierarchyEntity>>

    @Query("SELECT * FROM store_hierarchy ORDER BY division ASC, section ASC, department ASC")
    suspend fun getAllHierarchyList(): List<StoreHierarchyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHierarchy(node: StoreHierarchyEntity): Long

    @Delete
    suspend fun deleteHierarchy(node: StoreHierarchyEntity)
}

