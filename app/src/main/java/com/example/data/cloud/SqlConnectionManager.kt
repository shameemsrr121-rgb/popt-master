package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enterprise-Grade Live Microsoft SQL Server Connection & CRUD Manager for PO Master ERP.
 * Direct live JDBC execution on Microsoft SQL Server with transactional safety and isolation prefixing.
 */
class SqlConnectionManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SqlConnectionManager"

        @Volatile
        private var INSTANCE: SqlConnectionManager? = null

        fun getInstance(context: Context): SqlConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SqlConnectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val sharedPrefs = context.getSharedPreferences("po_master_sql_server_prefs", Context.MODE_PRIVATE)
    private val isConnectedState = AtomicBoolean(false)
    private val memoryCache = ConcurrentHashMap<String, Any>()

    private val forbiddenDatabases = setOf("master", "model", "msdb", "tempdb", "information_schema", "sys")

    // Current Server Credentials Configuration (must be provided via BuildConfig/env or user settings)
    var serverHost: String
        get() = sharedPrefs.getString("sql_server_host", BuildConfig.SQL_SERVER_HOST) ?: ""
        set(value) = sharedPrefs.edit().putString("sql_server_host", value.trim()).apply()

    var serverPort: Int
        get() = sharedPrefs.getString("sql_server_port", BuildConfig.SQL_SERVER_PORT.ifBlank { "1433" })?.toIntOrNull() ?: 1433
        set(value) = sharedPrefs.edit().putString("sql_server_port", value.toString()).apply()

    var databaseName: String
        get() {
            val name = sharedPrefs.getString("sql_database_name", BuildConfig.SQL_DATABASE_NAME.ifBlank { "pomaster_central_db" }) ?: "pomaster_central_db"
            return if (name.lowercase() in forbiddenDatabases) "pomaster_central_db" else name
        }
        set(value) {
            val safeName = if (value.trim().lowercase() in forbiddenDatabases) "pomaster_central_db" else value.trim()
            sharedPrefs.edit().putString("sql_database_name", safeName).apply()
        }

    var username: String
        get() = sharedPrefs.getString("sql_username", BuildConfig.SQL_SERVER_USER) ?: ""
        set(value) = sharedPrefs.edit().putString("sql_username", value.trim()).apply()

    var password: String
        get() = sharedPrefs.getString("sql_password", BuildConfig.SQL_SERVER_PASSWORD) ?: ""
        set(value) = sharedPrefs.edit().putString("sql_password", value).apply()

    var tablePrefix: String
        get() = sharedPrefs.getString("sql_table_prefix", BuildConfig.SQL_TABLE_PREFIX.ifBlank { "pomaster_" }) ?: "pomaster_"
        set(value) = sharedPrefs.edit().putString("sql_table_prefix", value.trim()).apply()

    fun escapeSql(value: String?): String {
        if (value == null) return "NULL"
        val clean = value.replace("'", "''")
        return "'$clean'"
    }

    private fun validateTableName(tableName: String): String {
        val prefix = tablePrefix.ifBlank { "pomaster_" }
        return if (tableName.startsWith(prefix)) tableName else "$prefix$tableName"
    }

    fun getServerConfig(): SqlServerSyncService.ServerConfig {
        return SqlServerSyncService.ServerConfig(
            host = serverHost,
            port = serverPort,
            database = databaseName,
            username = username,
            password = password,
            tablePrefix = tablePrefix
        )
    }

    private fun openConnection(): Connection {
        return SqlServerSyncService.getDirectConnection(getServerConfig())
    }

    suspend fun verifyConnection(): SqlServerSyncService.ConnectionResult = withContext(Dispatchers.IO) {
        val config = getServerConfig()
        val result = SqlServerSyncService.testConnection(config)
        isConnectedState.set(result.success)
        result
    }

    // =========================================================================
    // 1. COMPANY PROFILE CRUD OPERATIONS
    // =========================================================================

    suspend fun fetchCompanyProfile(): CompanyProfileEntity? = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("company_profile")
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                val rs = stmt.executeQuery("SELECT TOP 1 * FROM $table WHERE id = 1;")
                if (rs.next()) {
                    val profile = CompanyProfileEntity(
                        companyName = rs.getString("company_name") ?: "",
                        gstNumber = rs.getString("gst_number") ?: "",
                        address = rs.getString("address") ?: "",
                        phone = rs.getString("phone") ?: "",
                        email = rs.getString("email") ?: "",
                        website = rs.getString("website") ?: "",
                        bankName = rs.getString("bank_name") ?: "",
                        accountNumber = rs.getString("account_number") ?: "",
                        ifscCode = rs.getString("ifsc_code") ?: "",
                        termsAndConditions = rs.getString("terms_and_conditions") ?: ""
                    )
                    memoryCache["company_profile"] = profile
                    return@withContext profile
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCompanyProfile error", e)
        }
        memoryCache["company_profile"] as? CompanyProfileEntity
    }

    suspend fun saveCompanyProfile(profile: CompanyProfileEntity): Boolean = withContext(Dispatchers.IO) {
        memoryCache["company_profile"] = profile
        try {
            val table = validateTableName("company_profile")
            val sql = """
                IF NOT EXISTS (SELECT 1 FROM $table WHERE id = 1)
                    INSERT INTO $table (company_name, gst_number, address, phone, email, website, bank_name, account_number, ifsc_code, terms_and_conditions, updated_at)
                    VALUES (${escapeSql(profile.companyName)}, ${escapeSql(profile.gstNumber)}, ${escapeSql(profile.address)}, ${escapeSql(profile.phone)}, ${escapeSql(profile.email)}, ${escapeSql(profile.website)}, ${escapeSql(profile.bankName)}, ${escapeSql(profile.accountNumber)}, ${escapeSql(profile.ifscCode)}, ${escapeSql(profile.termsAndConditions)}, GETDATE())
                ELSE
                    UPDATE $table SET
                        company_name = ${escapeSql(profile.companyName)},
                        gst_number = ${escapeSql(profile.gstNumber)},
                        address = ${escapeSql(profile.address)},
                        phone = ${escapeSql(profile.phone)},
                        email = ${escapeSql(profile.email)},
                        website = ${escapeSql(profile.website)},
                        bank_name = ${escapeSql(profile.bankName)},
                        account_number = ${escapeSql(profile.accountNumber)},
                        ifsc_code = ${escapeSql(profile.ifscCode)},
                        terms_and_conditions = ${escapeSql(profile.termsAndConditions)},
                        updated_at = GETDATE()
                    WHERE id = 1;
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "saveCompanyProfile error", e)
            false
        }
    }

    // =========================================================================
    // 2. SUPPLIER MASTER CRUD OPERATIONS
    // =========================================================================

    suspend fun insertSupplier(supplier: SupplierEntity): Long = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("suppliers")
            val sql = """
                IF NOT EXISTS (SELECT 1 FROM $table WHERE code = ${escapeSql(supplier.code)})
                    INSERT INTO $table (code, name, contact_person, phone, email, address, payment_terms, rating, category, default_margin_percent, gst_number, is_active, created_at)
                    VALUES (${escapeSql(supplier.code)}, ${escapeSql(supplier.name)}, ${escapeSql(supplier.contactPerson)}, ${escapeSql(supplier.phone)}, ${escapeSql(supplier.email)}, ${escapeSql(supplier.address)}, ${escapeSql(supplier.paymentTerms)}, ${supplier.rating}, ${escapeSql(supplier.category)}, ${supplier.defaultMarginPercent}, ${escapeSql(supplier.gstNumber)}, ${if (supplier.isActive) 1 else 0}, GETDATE())
                ELSE
                    UPDATE $table SET
                        name = ${escapeSql(supplier.name)},
                        contact_person = ${escapeSql(supplier.contactPerson)},
                        phone = ${escapeSql(supplier.phone)},
                        email = ${escapeSql(supplier.email)},
                        address = ${escapeSql(supplier.address)},
                        payment_terms = ${escapeSql(supplier.paymentTerms)},
                        rating = ${supplier.rating},
                        category = ${escapeSql(supplier.category)},
                        default_margin_percent = ${supplier.defaultMarginPercent},
                        gst_number = ${escapeSql(supplier.gstNumber)},
                        is_active = ${if (supplier.isActive) 1 else 0}
                    WHERE code = ${escapeSql(supplier.code)};
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            supplier.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "insertSupplier error", e)
            0L
        }
    }

    suspend fun updateSupplier(supplier: SupplierEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("suppliers")
            val sql = """
                UPDATE $table SET
                    name = ${escapeSql(supplier.name)},
                    contact_person = ${escapeSql(supplier.contactPerson)},
                    phone = ${escapeSql(supplier.phone)},
                    email = ${escapeSql(supplier.email)},
                    address = ${escapeSql(supplier.address)},
                    payment_terms = ${escapeSql(supplier.paymentTerms)},
                    rating = ${supplier.rating},
                    category = ${escapeSql(supplier.category)},
                    default_margin_percent = ${supplier.defaultMarginPercent},
                    gst_number = ${escapeSql(supplier.gstNumber)},
                    is_active = ${if (supplier.isActive) 1 else 0}
                WHERE code = ${escapeSql(supplier.code)} OR id = ${supplier.id};
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateSupplier error", e)
            false
        }
    }

    suspend fun deleteSupplier(supplierId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("suppliers")
            val sql = "DELETE FROM $table WHERE id = $supplierId;"
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteSupplier error", e)
            false
        }
    }

    // =========================================================================
    // 3. INVENTORY CRUD OPERATIONS
    // =========================================================================

    suspend fun insertInventoryItem(item: InventoryEntity): Long = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("inventory")
            val sql = """
                IF NOT EXISTS (SELECT 1 FROM $table WHERE sku = ${escapeSql(item.sku)})
                    INSERT INTO $table (sku, item_name, category, unit, purchase_rate, selling_price, current_stock, reorder_level, location, updated_at)
                    VALUES (${escapeSql(item.sku)}, ${escapeSql(item.name)}, ${escapeSql(item.category)}, ${escapeSql(item.unit)}, ${item.unitCost}, ${item.standardSellingPrice}, ${item.currentStock}, ${item.minReorderLevel}, 'Central Warehouse', GETDATE())
                ELSE
                    UPDATE $table SET
                        item_name = ${escapeSql(item.name)},
                        category = ${escapeSql(item.category)},
                        unit = ${escapeSql(item.unit)},
                        purchase_rate = ${item.unitCost},
                        selling_price = ${item.standardSellingPrice},
                        current_stock = ${item.currentStock},
                        reorder_level = ${item.minReorderLevel},
                        updated_at = GETDATE()
                    WHERE sku = ${escapeSql(item.sku)};
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            item.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "insertInventoryItem error", e)
            0L
        }
    }

    suspend fun updateInventoryItem(item: InventoryEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("inventory")
            val sql = """
                UPDATE $table SET
                    item_name = ${escapeSql(item.name)},
                    category = ${escapeSql(item.category)},
                    unit = ${escapeSql(item.unit)},
                    purchase_rate = ${item.unitCost},
                    selling_price = ${item.standardSellingPrice},
                    current_stock = ${item.currentStock},
                    reorder_level = ${item.minReorderLevel},
                    updated_at = GETDATE()
                WHERE sku = ${escapeSql(item.sku)} OR id = ${item.id};
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "updateInventoryItem error", e)
            false
        }
    }

    suspend fun deleteInventoryItem(itemId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("inventory")
            val sql = "DELETE FROM $table WHERE id = $itemId;"
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "deleteInventoryItem error", e)
            false
        }
    }

    suspend fun adjustInventoryStock(itemId: Long, qty: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("inventory")
            val sql = "UPDATE $table SET current_stock = current_stock + $qty, updated_at = GETDATE() WHERE id = $itemId;"
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "adjustInventoryStock error", e)
            false
        }
    }

    // =========================================================================
    // 4. PURCHASE ORDER & LINE ITEMS
    // =========================================================================

    suspend fun createPurchaseOrder(po: PurchaseOrderEntity, items: List<PurchaseOrderItemEntity>): Long = withContext(Dispatchers.IO) {
        try {
            val poTable = validateTableName("purchase_orders")
            val itemsTable = validateTableName("po_items")

            openConnection().use { conn ->
                conn.autoCommit = false
                val stmt = conn.createStatement()

                // Upsert PO
                val poSql = """
                    IF NOT EXISTS (SELECT 1 FROM $poTable WHERE po_number = ${escapeSql(po.poNumber)})
                        INSERT INTO $poTable (po_number, supplier_id, supplier_name, order_date_millis, expected_delivery_date_millis, payment_terms, status, subtotal, tax_amount, total_amount, location, notes, created_at)
                        VALUES (${escapeSql(po.poNumber)}, ${po.supplierId}, ${escapeSql(po.supplierName)}, ${po.orderDateMillis}, ${po.expectedDeliveryDateMillis}, ${escapeSql(po.paymentTerms)}, ${escapeSql(po.status)}, ${po.subtotal}, ${po.taxAmount}, ${po.totalAmount}, ${escapeSql(po.location)}, ${escapeSql(po.notes)}, GETDATE())
                    ELSE
                        UPDATE $poTable SET
                            supplier_name = ${escapeSql(po.supplierName)},
                            expected_delivery_date_millis = ${po.expectedDeliveryDateMillis},
                            payment_terms = ${escapeSql(po.paymentTerms)},
                            status = ${escapeSql(po.status)},
                            subtotal = ${po.subtotal},
                            tax_amount = ${po.taxAmount},
                            total_amount = ${po.totalAmount},
                            location = ${escapeSql(po.location)},
                            notes = ${escapeSql(po.notes)}
                        WHERE po_number = ${escapeSql(po.poNumber)};
                """.trimIndent()
                stmt.executeUpdate(poSql)

                // Get PO ID from table
                var sqlPoId = 1L
                val rs = stmt.executeQuery("SELECT id FROM $poTable WHERE po_number = ${escapeSql(po.poNumber)}")
                if (rs.next()) {
                    sqlPoId = rs.getLong("id")
                }
                rs.close()

                // Insert Items
                for (item in items) {
                    val itemSql = """
                        INSERT INTO $itemsTable (po_id, item_id, item_name, category, ordered_quantity, received_quantity, rate, tax_percent, item_total, is_cancelled)
                        VALUES ($sqlPoId, ${item.inventoryItemId}, ${escapeSql(item.itemName)}, ${escapeSql(item.c1Brand)}, ${item.quantityOrdered}, ${item.quantityReceived}, ${item.unitPrice}, ${item.taxPercent}, ${item.totalPrice}, 0);
                    """.trimIndent()
                    stmt.executeUpdate(itemSql)
                }

                conn.commit()
            }
            po.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "createPurchaseOrder error", e)
            0L
        }
    }

    suspend fun updatePurchaseOrder(po: PurchaseOrderEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val poTable = validateTableName("purchase_orders")
            val poSql = """
                UPDATE $poTable SET
                    supplier_name = ${escapeSql(po.supplierName)},
                    expected_delivery_date_millis = ${po.expectedDeliveryDateMillis},
                    payment_terms = ${escapeSql(po.paymentTerms)},
                    status = ${escapeSql(po.status)},
                    subtotal = ${po.subtotal},
                    tax_amount = ${po.taxAmount},
                    total_amount = ${po.totalAmount},
                    location = ${escapeSql(po.location)},
                    notes = ${escapeSql(po.notes)}
                WHERE po_number = ${escapeSql(po.poNumber)} OR id = ${po.id};
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(poSql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "updatePurchaseOrder error", e)
            false
        }
    }

    suspend fun deletePurchaseOrder(poId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val poTable = validateTableName("purchase_orders")
            val sql = "DELETE FROM $poTable WHERE id = $poId;"
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "deletePurchaseOrder error", e)
            false
        }
    }

    // =========================================================================
    // 5. PURCHASE PLANS
    // =========================================================================

    suspend fun insertPurchasePlanNote(note: PurchasePlanNoteEntity): Long = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("purchase_plans")
            val sql = """
                INSERT INTO $table (title, planned_date_millis, to_date_millis, location, notes, estimated_budget, category_or_supplier, priority, status, reminder_days_before, created_at_millis)
                VALUES (${escapeSql(note.title)}, ${note.plannedDateMillis}, ${note.toDateMillis}, ${escapeSql(note.location)}, ${escapeSql(note.notes)}, ${note.estimatedBudget}, ${escapeSql(note.categoryOrSupplier)}, ${escapeSql(note.priority)}, ${escapeSql(note.status)}, ${note.reminderDaysBefore}, ${note.createdAtMillis});
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            note.id.takeIf { it > 0 } ?: System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "insertPurchasePlanNote error", e)
            0L
        }
    }

    suspend fun updatePurchasePlanNote(note: PurchasePlanNoteEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val table = validateTableName("purchase_plans")
            val sql = """
                UPDATE $table SET
                    title = ${escapeSql(note.title)},
                    planned_date_millis = ${note.plannedDateMillis},
                    to_date_millis = ${note.toDateMillis},
                    location = ${escapeSql(note.location)},
                    notes = ${escapeSql(note.notes)},
                    estimated_budget = ${note.estimatedBudget},
                    category_or_supplier = ${escapeSql(note.categoryOrSupplier)},
                    priority = ${escapeSql(note.priority)},
                    status = ${escapeSql(note.status)},
                    reminder_days_before = ${note.reminderDaysBefore}
                WHERE id = ${note.id} OR (title = ${escapeSql(note.title)} AND planned_date_millis = ${note.plannedDateMillis});
            """.trimIndent()
            openConnection().use { conn ->
                val stmt = conn.createStatement()
                stmt.executeUpdate(sql)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "updatePurchasePlanNote error", e)
            false
        }
    }
}
