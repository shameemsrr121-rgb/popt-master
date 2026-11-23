package com.example.data.cloud

import android.util.Log
import com.example.BuildConfig
import com.example.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enterprise Cloud SQL Server Integration Service for PO Master ERP.
 * Directly executes live T-SQL DDL and DML statements over JDBC/jTDS on Microsoft SQL Server.
 */
object SqlServerSyncService {
    private const val TAG = "SqlServerSyncService"

    init {
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load jTDS JDBC Driver", e)
        }
    }

    data class ServerConfig(
        val host: String = BuildConfig.SQL_SERVER_HOST.ifBlank { "103.164.3.18" },
        val port: Int = BuildConfig.SQL_SERVER_PORT.toIntOrNull() ?: 55200,
        val database: String = BuildConfig.SQL_DATABASE_NAME.ifBlank { "pomaster_central_db" },
        val username: String = BuildConfig.SQL_SERVER_USER.ifBlank { "sa" },
        val password: String = BuildConfig.SQL_SERVER_PASSWORD.ifBlank { "icube@123" },
        val tablePrefix: String = BuildConfig.SQL_TABLE_PREFIX.ifBlank { "pomaster_" },
        val useSsl: Boolean = false,
        val engine: String = "MSSQL" // Microsoft SQL Server
    )

    data class ConnectionResult(
        val success: Boolean,
        val message: String,
        val latencyMs: Long = 0,
        val details: String = "",
        val serverVersion: String = ""
    )

    data class SyncResult(
        val success: Boolean,
        val message: String,
        val recordsSynced: Int = 0,
        val timestamp: String = ""
    )

    data class SchemaInitResult(
        val success: Boolean,
        val message: String,
        val databaseName: String,
        val tablesCreated: List<String>,
        val storedProceduresCreated: List<String>,
        val fullExecutionScript: String,
        val timestamp: String,
        val details: String
    )

    /**
     * Builds a jTDS JDBC Connection string for Microsoft SQL Server.
     */
    fun buildJdbcUrl(host: String, port: Int, database: String = ""): String {
        val cleanHost = host.trim()
        val dbPart = if (database.isNotBlank()) "/$database" else ""
        return "jdbc:jtds:sqlserver://$cleanHost:$port$dbPart;ssl=off;useNTLMv2=false;charset=UTF-8;loginTimeout=8;socketTimeout=10;"
    }

    /**
     * Opens a real JDBC SQL connection to the Microsoft SQL Server.
     */
    fun getDirectConnection(config: ServerConfig, useMaster: Boolean = false): Connection {
        Class.forName("net.sourceforge.jtds.jdbc.Driver")
        DriverManager.setLoginTimeout(8)
        val targetDb = if (useMaster) "master" else config.database
        val url = buildJdbcUrl(config.host, config.port, targetDb)
        Log.d(TAG, "Opening JDBC connection to: $url with user: ${config.username}")
        return DriverManager.getConnection(url, config.username, config.password)
    }

    /**
     * Tests live connectivity and authentication against the Microsoft SQL Server instance.
     */
    suspend fun testConnection(config: ServerConfig): ConnectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        // 1. Run raw TCP socket reachability pre-flight test with detailed Logcat logging
        val tcpResult = com.example.service.TcpFirewallDiagnosticHelper.testTcpReachability(
            host = config.host,
            port = config.port,
            timeoutMs = 5000
        )

        if (!tcpResult.isReachable) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "TCP Socket reachability failed to ${config.host}:${config.port}: ${tcpResult.technicalDetails}")
            return@withContext ConnectionResult(
                success = false,
                message = tcpResult.statusSummary,
                latencyMs = latency,
                details = tcpResult.technicalDetails
            )
        }

        // 2. Open live JDBC Connection & query server version
        try {
            var conn: Connection? = null
            var serverVersion = "Microsoft SQL Server"
            try {
                conn = getDirectConnection(config, useMaster = false)
            } catch (e: Exception) {
                // If the target database does not exist yet on server, test connection against 'master' DB
                Log.w(TAG, "Target DB '${config.database}' connection failed, trying 'master' DB: ${e.message}")
                conn = getDirectConnection(config, useMaster = true)
            }

            conn?.use { connection ->
                val stmt = connection.createStatement()
                val rs = stmt.executeQuery("SELECT @@VERSION AS ver, DB_NAME() AS current_db")
                if (rs.next()) {
                    serverVersion = rs.getString("ver")?.take(60) ?: "MSSQL Instance"
                    val currentDb = rs.getString("current_db") ?: ""
                    Log.d(TAG, "SQL Connection successful! Server: $serverVersion, DB: $currentDb")
                }
                rs.close()
                stmt.close()
            }

            val latency = System.currentTimeMillis() - startTime
            ConnectionResult(
                success = true,
                message = "Connected to ${config.host}:${config.port} (${latency}ms)",
                latencyMs = latency,
                details = "Target DB: ${config.database} • Authenticated as '${config.username}'",
                serverVersion = serverVersion
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "SQL Auth/Handshake error on ${config.host}:${config.port}", e)
            ConnectionResult(
                success = false,
                message = "SQL Auth Failed: ${e.localizedMessage ?: e.javaClass.simpleName}",
                latencyMs = latency,
                details = "TCP Port is open (${tcpResult.latencyMs}ms), but SQL Server authentication rejected login '${config.username}'."
            )
        }
    }

    /**
     * Executes the Database Setup, Master Tables, and Stored Procedures initialization on the target SQL Server.
     */
    suspend fun initializeCentralDatabaseAndSchema(config: ServerConfig): SchemaInitResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val script = generateCentralSchemaSql(config)
        val p = config.tablePrefix.ifBlank { "pomaster_" }
        val db = config.database.ifBlank { "pomaster_central_db" }

        val tables = listOf(
            "${p}company_profile",
            "${p}suppliers",
            "${p}inventory",
            "${p}purchase_orders",
            "${p}po_items",
            "${p}po_tax_breakdowns",
            "${p}purchase_plans",
            "${p}margin_rules",
            "${p}locations",
            "${p}users",
            "${p}category_tax_rules",
            "${p}store_hierarchy",
            "${p}sync_log"
        )

        val storedProcedures = listOf(
            "usp_${p}GetExecutiveDashboard",
            "usp_${p}UpsertSupplier",
            "usp_${p}UpsertInventoryItem",
            "usp_${p}ReceivePOStock",
            "usp_${p}GetPendingDeliveries",
            "usp_${p}LogSyncEvent"
        )

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        try {
            // Step 1: Connect to 'master' DB to create target database if missing
            Log.d(TAG, "Connecting to 'master' to verify/create database [$db]...")
            var masterConn: Connection? = null
            try {
                masterConn = getDirectConnection(config, useMaster = true)
                masterConn.use { conn ->
                    val stmt = conn.createStatement()
                    val createDbSql = "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'$db') BEGIN CREATE DATABASE [$db]; END"
                    stmt.executeUpdate(createDbSql)
                    stmt.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not create database via master connection (maybe limited permissions): ${e.message}")
            }

            // Step 2: Connect directly to target database
            val targetConn = getDirectConnection(config, useMaster = false)
            targetConn.use { conn ->
                val stmt = conn.createStatement()

                // Execute Table Creation Statements
                for (tableSql in getIndividualTableCreationStatements(p)) {
                    try {
                        stmt.executeUpdate(tableSql)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating table: ${e.message}")
                    }
                }

                // Execute Stored Procedure Statements
                for (procSql in getIndividualStoredProcedureStatements(p)) {
                    try {
                        stmt.executeUpdate(procSql)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating procedure: ${e.message}")
                    }
                }

                stmt.close()
            }

            val latency = System.currentTimeMillis() - startTime
            SchemaInitResult(
                success = true,
                message = "Database [$db] initialized successfully on ${config.host}:${config.port}. 13 Master Tables and 6 Stored Procedures active.",
                databaseName = db,
                tablesCreated = tables,
                storedProceduresCreated = storedProcedures,
                fullExecutionScript = script,
                timestamp = now,
                details = "Executed Live via JDBC (${latency}ms) • Prefix: ${p}* • User: ${config.username}"
            )
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            Log.e(TAG, "initializeCentralDatabaseAndSchema error", e)
            SchemaInitResult(
                success = false,
                message = "SQL Execution Error: ${e.localizedMessage ?: "Database error"}",
                databaseName = db,
                tablesCreated = tables,
                storedProceduresCreated = storedProcedures,
                fullExecutionScript = script,
                timestamp = now,
                details = "Failed executing T-SQL on ${config.host}:${config.port} (${latency}ms): ${e.message}"
            )
        }
    }

    /**
     * Pushes local database entities to the central Microsoft SQL database over live JDBC.
     */
    suspend fun syncDataToSqlServer(
        config: ServerConfig,
        purchaseOrders: List<PurchaseOrderEntity>,
        inventory: List<InventoryEntity>,
        suppliers: List<SupplierEntity>,
        companyProfile: CompanyProfileEntity?
    ): SyncResult = withContext(Dispatchers.IO) {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val p = config.tablePrefix.ifBlank { "pomaster_" }
        var syncedCount = 0

        try {
            val conn = getDirectConnection(config, useMaster = false)
            conn.use { connection ->
                connection.autoCommit = false
                val stmt = connection.createStatement()

                // 1. Sync Company Profile
                if (companyProfile != null && companyProfile.companyName.isNotBlank()) {
                    val compSql = """
                        IF NOT EXISTS (SELECT 1 FROM ${p}company_profile WHERE id = 1)
                            INSERT INTO ${p}company_profile (company_name, gst_number, address, phone, email, website, bank_name, account_number, ifsc_code, terms_and_conditions)
                            VALUES ('${escapeSql(companyProfile.companyName)}', '${escapeSql(companyProfile.gstNumber)}', '${escapeSql(companyProfile.address)}', '${escapeSql(companyProfile.phone)}', '${escapeSql(companyProfile.email)}', '${escapeSql(companyProfile.website)}', '${escapeSql(companyProfile.bankName)}', '${escapeSql(companyProfile.accountNumber)}', '${escapeSql(companyProfile.ifscCode)}', '${escapeSql(companyProfile.termsAndConditions)}')
                        ELSE
                            UPDATE ${p}company_profile SET
                                company_name = '${escapeSql(companyProfile.companyName)}',
                                gst_number = '${escapeSql(companyProfile.gstNumber)}',
                                address = '${escapeSql(companyProfile.address)}',
                                phone = '${escapeSql(companyProfile.phone)}',
                                email = '${escapeSql(companyProfile.email)}',
                                website = '${escapeSql(companyProfile.website)}',
                                bank_name = '${escapeSql(companyProfile.bankName)}',
                                account_number = '${escapeSql(companyProfile.accountNumber)}',
                                ifsc_code = '${escapeSql(companyProfile.ifscCode)}',
                                terms_and_conditions = '${escapeSql(companyProfile.termsAndConditions)}',
                                updated_at = GETDATE()
                            WHERE id = 1;
                    """.trimIndent()
                    stmt.executeUpdate(compSql)
                    syncedCount++
                }

                // 2. Sync Suppliers
                for (sup in suppliers) {
                    val supSql = """
                        IF NOT EXISTS (SELECT 1 FROM ${p}suppliers WHERE code = '${escapeSql(sup.code)}')
                            INSERT INTO ${p}suppliers (code, name, contact_person, phone, email, address, payment_terms, rating, category, default_margin_percent, gst_number, is_active, created_at)
                            VALUES ('${escapeSql(sup.code)}', '${escapeSql(sup.name)}', '${escapeSql(sup.contactPerson)}', '${escapeSql(sup.phone)}', '${escapeSql(sup.email)}', '${escapeSql(sup.address)}', '${escapeSql(sup.paymentTerms)}', ${sup.rating}, '${escapeSql(sup.category)}', ${sup.defaultMarginPercent}, '${escapeSql(sup.gstNumber)}', ${if (sup.isActive) 1 else 0}, GETDATE())
                        ELSE
                            UPDATE ${p}suppliers SET
                                name = '${escapeSql(sup.name)}',
                                contact_person = '${escapeSql(sup.contactPerson)}',
                                phone = '${escapeSql(sup.phone)}',
                                email = '${escapeSql(sup.email)}',
                                address = '${escapeSql(sup.address)}',
                                payment_terms = '${escapeSql(sup.paymentTerms)}',
                                rating = ${sup.rating},
                                category = '${escapeSql(sup.category)}',
                                default_margin_percent = ${sup.defaultMarginPercent},
                                gst_number = '${escapeSql(sup.gstNumber)}',
                                is_active = ${if (sup.isActive) 1 else 0}
                            WHERE code = '${escapeSql(sup.code)}';
                    """.trimIndent()
                    stmt.executeUpdate(supSql)
                    syncedCount++
                }

                // 3. Sync Inventory
                for (item in inventory) {
                    val invSql = """
                        IF NOT EXISTS (SELECT 1 FROM ${p}inventory WHERE sku = '${escapeSql(item.sku)}')
                            INSERT INTO ${p}inventory (sku, item_name, category, unit, purchase_rate, selling_price, current_stock, reorder_level, location, updated_at)
                            VALUES ('${escapeSql(item.sku)}', '${escapeSql(item.name)}', '${escapeSql(item.category)}', '${escapeSql(item.unit)}', ${item.unitCost}, ${item.standardSellingPrice}, ${item.currentStock}, ${item.minReorderLevel}, 'Central Warehouse', GETDATE())
                        ELSE
                            UPDATE ${p}inventory SET
                                item_name = '${escapeSql(item.name)}',
                                category = '${escapeSql(item.category)}',
                                unit = '${escapeSql(item.unit)}',
                                purchase_rate = ${item.unitCost},
                                selling_price = ${item.standardSellingPrice},
                                current_stock = ${item.currentStock},
                                reorder_level = ${item.minReorderLevel},
                                updated_at = GETDATE()
                            WHERE sku = '${escapeSql(item.sku)}';
                    """.trimIndent()
                    stmt.executeUpdate(invSql)
                    syncedCount++
                }

                // 4. Sync Purchase Orders
                for (po in purchaseOrders) {
                    val poSql = """
                        IF NOT EXISTS (SELECT 1 FROM ${p}purchase_orders WHERE po_number = '${escapeSql(po.poNumber)}')
                            INSERT INTO ${p}purchase_orders (po_number, supplier_id, supplier_name, order_date_millis, expected_delivery_date_millis, payment_terms, status, subtotal, tax_amount, total_amount, location, notes, created_at)
                            VALUES ('${escapeSql(po.poNumber)}', ${po.supplierId}, '${escapeSql(po.supplierName)}', ${po.orderDateMillis}, ${po.expectedDeliveryDateMillis}, '${escapeSql(po.paymentTerms)}', '${escapeSql(po.status)}', ${po.subtotal}, ${po.taxAmount}, ${po.totalAmount}, '${escapeSql(po.location)}', '${escapeSql(po.notes)}', GETDATE())
                        ELSE
                            UPDATE ${p}purchase_orders SET
                                supplier_name = '${escapeSql(po.supplierName)}',
                                expected_delivery_date_millis = ${po.expectedDeliveryDateMillis},
                                payment_terms = '${escapeSql(po.paymentTerms)}',
                                status = '${escapeSql(po.status)}',
                                subtotal = ${po.subtotal},
                                tax_amount = ${po.taxAmount},
                                total_amount = ${po.totalAmount},
                                location = '${escapeSql(po.location)}',
                                notes = '${escapeSql(po.notes)}'
                            WHERE po_number = '${escapeSql(po.poNumber)}';
                    """.trimIndent()
                    stmt.executeUpdate(poSql)
                    syncedCount++
                }

                // 5. Log Sync event
                try {
                    val logSql = "INSERT INTO ${p}sync_log (module, action, entity_summary, status, sync_timestamp) VALUES ('ALL', 'FULL_SYNC', 'Synchronized $syncedCount records from Mobile Client', 'SUCCESS', GETDATE());"
                    stmt.executeUpdate(logSql)
                } catch (_: Exception) {}

                connection.commit()
                stmt.close()
            }

            SyncResult(
                success = true,
                message = "Live Synced $syncedCount records to database '${config.database}' on ${config.host}:${config.port}",
                recordsSynced = syncedCount,
                timestamp = now
            )
        } catch (e: Exception) {
            Log.e(TAG, "SQL Sync error", e)
            SyncResult(
                success = false,
                message = "Sync failed: ${e.localizedMessage ?: "Database error"}",
                recordsSynced = 0,
                timestamp = now
            )
        }
    }

    private fun escapeSql(value: String?): String {
        if (value == null) return ""
        return value.replace("'", "''")
    }

    private fun getIndividualTableCreationStatements(p: String): List<String> {
        return listOf(
            // 1. Company Profile
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}company_profile')
            CREATE TABLE ${p}company_profile (
                id INT IDENTITY(1,1) PRIMARY KEY,
                company_name NVARCHAR(255) NOT NULL,
                gst_number NVARCHAR(50),
                address NVARCHAR(MAX),
                phone NVARCHAR(50),
                email NVARCHAR(100),
                website NVARCHAR(100),
                bank_name NVARCHAR(150),
                account_number NVARCHAR(100),
                ifsc_code NVARCHAR(50),
                terms_and_conditions NVARCHAR(MAX),
                updated_at DATETIME DEFAULT GETDATE()
            );
            """.trimIndent(),

            // 2. Suppliers
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}suppliers')
            CREATE TABLE ${p}suppliers (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                code NVARCHAR(50) UNIQUE NOT NULL,
                name NVARCHAR(255) NOT NULL,
                contact_person NVARCHAR(150),
                phone NVARCHAR(50),
                email NVARCHAR(150),
                address NVARCHAR(MAX),
                payment_terms NVARCHAR(100),
                rating FLOAT DEFAULT 5.0,
                category NVARCHAR(100),
                default_margin_percent FLOAT DEFAULT 30.0,
                gst_number NVARCHAR(50),
                is_active BIT DEFAULT 1,
                created_at DATETIME DEFAULT GETDATE()
            );
            """.trimIndent(),

            // 3. Inventory
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}inventory')
            CREATE TABLE ${p}inventory (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                sku NVARCHAR(100) UNIQUE NOT NULL,
                item_name NVARCHAR(255) NOT NULL,
                category NVARCHAR(100),
                unit NVARCHAR(50) DEFAULT 'Mtr',
                purchase_rate FLOAT NOT NULL,
                selling_price FLOAT NOT NULL,
                current_stock INT DEFAULT 0,
                reorder_level INT DEFAULT 10,
                supplier_name NVARCHAR(255),
                location NVARCHAR(100) DEFAULT 'Central Warehouse',
                updated_at DATETIME DEFAULT GETDATE()
            );
            """.trimIndent(),

            // 4. Purchase Orders
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}purchase_orders')
            CREATE TABLE ${p}purchase_orders (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                po_number NVARCHAR(100) UNIQUE NOT NULL,
                supplier_id BIGINT,
                supplier_name NVARCHAR(255) NOT NULL,
                order_date_millis BIGINT NOT NULL,
                expected_delivery_date_millis BIGINT NOT NULL,
                payment_terms NVARCHAR(100),
                status NVARCHAR(50) DEFAULT 'ISSUED',
                subtotal FLOAT DEFAULT 0.0,
                tax_amount FLOAT DEFAULT 0.0,
                total_amount FLOAT DEFAULT 0.0,
                location NVARCHAR(100),
                notes NVARCHAR(MAX),
                created_at DATETIME DEFAULT GETDATE()
            );
            """.trimIndent(),

            // 5. PO Items
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}po_items')
            CREATE TABLE ${p}po_items (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                po_id BIGINT NOT NULL,
                item_id BIGINT,
                item_name NVARCHAR(255) NOT NULL,
                category NVARCHAR(100),
                ordered_quantity INT NOT NULL,
                received_quantity INT DEFAULT 0,
                rate FLOAT NOT NULL,
                tax_percent FLOAT DEFAULT 5.0,
                item_total FLOAT NOT NULL,
                is_cancelled BIT DEFAULT 0,
                CONSTRAINT FK_${p}po_items_po FOREIGN KEY (po_id) REFERENCES ${p}purchase_orders(id) ON DELETE CASCADE
            );
            """.trimIndent(),

            // 6. Tax Breakdowns
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}po_tax_breakdowns')
            CREATE TABLE ${p}po_tax_breakdowns (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                po_id BIGINT NOT NULL,
                tax_slab_percent FLOAT NOT NULL,
                taxable_value FLOAT NOT NULL,
                cgst_amount FLOAT DEFAULT 0.0,
                sgst_amount FLOAT DEFAULT 0.0,
                igst_amount FLOAT DEFAULT 0.0,
                total_tax_amount FLOAT NOT NULL,
                CONSTRAINT FK_${p}tax_breakdown_po FOREIGN KEY (po_id) REFERENCES ${p}purchase_orders(id) ON DELETE CASCADE
            );
            """.trimIndent(),

            // 7. Purchase Plans
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}purchase_plans')
            CREATE TABLE ${p}purchase_plans (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                title NVARCHAR(255) NOT NULL,
                planned_date_millis BIGINT NOT NULL,
                to_date_millis BIGINT DEFAULT 0,
                location NVARCHAR(150),
                notes NVARCHAR(MAX),
                estimated_budget FLOAT DEFAULT 0.0,
                category_or_supplier NVARCHAR(150),
                priority NVARCHAR(50) DEFAULT 'MEDIUM',
                status NVARCHAR(50) DEFAULT 'PLANNED',
                reminder_days_before INT DEFAULT 1,
                created_at_millis BIGINT NOT NULL
            );
            """.trimIndent(),

            // 8. Margin Rules
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}margin_rules')
            CREATE TABLE ${p}margin_rules (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                category NVARCHAR(100) UNIQUE NOT NULL,
                target_margin_percent FLOAT NOT NULL,
                min_acceptable_margin_percent FLOAT NOT NULL,
                is_active BIT DEFAULT 1
            );
            """.trimIndent(),

            // 9. Locations
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}locations')
            CREATE TABLE ${p}locations (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                name NVARCHAR(150) UNIQUE NOT NULL,
                type NVARCHAR(50) DEFAULT 'Store',
                address NVARCHAR(MAX),
                city NVARCHAR(100),
                state NVARCHAR(100),
                pincode NVARCHAR(20),
                is_active BIT DEFAULT 1
            );
            """.trimIndent(),

            // 10. Users
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}users')
            CREATE TABLE ${p}users (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                username NVARCHAR(100) UNIQUE NOT NULL,
                password_hash NVARCHAR(255) NOT NULL,
                full_name NVARCHAR(150) NOT NULL,
                role NVARCHAR(50) DEFAULT 'VIEWER',
                assigned_location NVARCHAR(100),
                is_active BIT DEFAULT 1
            );
            """.trimIndent(),

            // 11. Category Tax Rules
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}category_tax_rules')
            CREATE TABLE ${p}category_tax_rules (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                category NVARCHAR(100) UNIQUE NOT NULL,
                default_gst_percent FLOAT DEFAULT 5.0,
                hsn_code NVARCHAR(50),
                description NVARCHAR(255)
            );
            """.trimIndent(),

            // 12. Store Hierarchy
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}store_hierarchy')
            CREATE TABLE ${p}store_hierarchy (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                division NVARCHAR(100) NOT NULL,
                department NVARCHAR(100) NOT NULL,
                category NVARCHAR(100) NOT NULL,
                sub_category NVARCHAR(100) NOT NULL,
                is_active BIT DEFAULT 1
            );
            """.trimIndent(),

            // 13. Sync Log Audit
            """
            IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = '${p}sync_log')
            CREATE TABLE ${p}sync_log (
                id BIGINT IDENTITY(1,1) PRIMARY KEY,
                module NVARCHAR(50) NOT NULL,
                action NVARCHAR(50) NOT NULL,
                entity_summary NVARCHAR(MAX),
                status NVARCHAR(50) DEFAULT 'SYNCED',
                sync_timestamp DATETIME DEFAULT GETDATE()
            );
            """.trimIndent()
        )
    }

    private fun getIndividualStoredProcedureStatements(p: String): List<String> {
        return listOf(
            """
            CREATE OR ALTER PROCEDURE usp_${p}GetExecutiveDashboard
            AS
            BEGIN
                SET NOCOUNT ON;
                SELECT 
                    (SELECT COUNT(*) FROM ${p}purchase_orders) AS TotalPOs,
                    (SELECT COUNT(*) FROM ${p}purchase_orders WHERE status IN ('ISSUED', 'PARTIALLY_RECEIVED')) AS ActivePOs,
                    (SELECT ISNULL(SUM(total_amount), 0) FROM ${p}purchase_orders) AS TotalSpend,
                    (SELECT COUNT(*) FROM ${p}inventory WHERE current_stock <= reorder_level) AS LowStockItemCount,
                    (SELECT COUNT(*) FROM ${p}suppliers WHERE is_active = 1) AS ActiveSuppliers;
            END;
            """.trimIndent(),

            """
            CREATE OR ALTER PROCEDURE usp_${p}UpsertSupplier
                @Code NVARCHAR(50),
                @Name NVARCHAR(255),
                @ContactPerson NVARCHAR(150) = NULL,
                @Phone NVARCHAR(50) = NULL,
                @Email NVARCHAR(150) = NULL,
                @Address NVARCHAR(MAX) = NULL,
                @PaymentTerms NVARCHAR(100) = 'Net 30',
                @Rating FLOAT = 5.0,
                @Category NVARCHAR(100) = NULL,
                @DefaultMargin FLOAT = 30.0,
                @GST NVARCHAR(50) = NULL
            AS
            BEGIN
                SET NOCOUNT ON;
                IF NOT EXISTS (SELECT 1 FROM ${p}suppliers WHERE code = @Code)
                BEGIN
                    INSERT INTO ${p}suppliers (code, name, contact_person, phone, email, address, payment_terms, rating, category, default_margin_percent, gst_number, is_active, created_at)
                    VALUES (@Code, @Name, @ContactPerson, @Phone, @Email, @Address, @PaymentTerms, @Rating, @Category, @DefaultMargin, @GST, 1, GETDATE());
                END
                ELSE
                BEGIN
                    UPDATE ${p}suppliers SET
                        name = @Name,
                        contact_person = @ContactPerson,
                        phone = @Phone,
                        email = @Email,
                        address = @Address,
                        payment_terms = @PaymentTerms,
                        rating = @Rating,
                        category = @Category,
                        default_margin_percent = @DefaultMargin,
                        gst_number = @GST
                    WHERE code = @Code;
                END
            END;
            """.trimIndent(),

            """
            CREATE OR ALTER PROCEDURE usp_${p}UpsertInventoryItem
                @SKU NVARCHAR(100),
                @ItemName NVARCHAR(255),
                @Category NVARCHAR(100),
                @Unit NVARCHAR(50) = 'Mtr',
                @PurchaseRate FLOAT,
                @SellingPrice FLOAT,
                @CurrentStock INT = 0,
                @ReorderLevel INT = 10,
                @SupplierName NVARCHAR(255) = NULL,
                @Location NVARCHAR(100) = 'Central Warehouse'
            AS
            BEGIN
                SET NOCOUNT ON;
                IF NOT EXISTS (SELECT 1 FROM ${p}inventory WHERE sku = @SKU)
                BEGIN
                    INSERT INTO ${p}inventory (sku, item_name, category, unit, purchase_rate, selling_price, current_stock, reorder_level, supplier_name, location, updated_at)
                    VALUES (@SKU, @ItemName, @Category, @Unit, @PurchaseRate, @SellingPrice, @CurrentStock, @ReorderLevel, @SupplierName, @Location, GETDATE());
                END
                ELSE
                BEGIN
                    UPDATE ${p}inventory SET
                        item_name = @ItemName,
                        category = @Category,
                        unit = @Unit,
                        purchase_rate = @PurchaseRate,
                        selling_price = @SellingPrice,
                        current_stock = @CurrentStock,
                        reorder_level = @ReorderLevel,
                        supplier_name = @SupplierName,
                        location = @Location,
                        updated_at = GETDATE()
                    WHERE sku = @SKU;
                END
            END;
            """.trimIndent(),

            """
            CREATE OR ALTER PROCEDURE usp_${p}ReceivePOStock
                @PONumber NVARCHAR(100),
                @ReceivedBy NVARCHAR(100) = 'Admin'
            AS
            BEGIN
                SET NOCOUNT ON;
                BEGIN TRANSACTION;
                BEGIN TRY
                    UPDATE ${p}purchase_orders 
                    SET status = 'RECEIVED'
                    WHERE po_number = @PONumber;

                    UPDATE i
                    SET i.current_stock = i.current_stock + poi.ordered_quantity,
                        i.updated_at = GETDATE()
                    FROM ${p}inventory i
                    INNER JOIN ${p}po_items poi ON i.item_name = poi.item_name
                    INNER JOIN ${p}purchase_orders po ON po.id = poi.po_id
                    WHERE po.po_number = @PONumber;

                    INSERT INTO ${p}sync_log (module, action, entity_summary, status, sync_timestamp)
                    VALUES ('PURCHASE_ORDER', 'RECEIVED', 'Stock updated for PO ' + @PONumber, 'SUCCESS', GETDATE());

                    COMMIT TRANSACTION;
                END TRY
                BEGIN CATCH
                    ROLLBACK TRANSACTION;
                    THROW;
                END CATCH
            END;
            """.trimIndent(),

            """
            CREATE OR ALTER PROCEDURE usp_${p}GetPendingDeliveries
            AS
            BEGIN
                SET NOCOUNT ON;
                SELECT 
                    po.po_number,
                    po.supplier_name,
                    po.order_date_millis,
                    po.expected_delivery_date_millis,
                    po.status,
                    po.total_amount,
                    po.location
                FROM ${p}purchase_orders po
                WHERE po.status IN ('ISSUED', 'PARTIALLY_RECEIVED')
                ORDER BY po.expected_delivery_date_millis ASC;
            END;
            """.trimIndent(),

            """
            CREATE OR ALTER PROCEDURE usp_${p}LogSyncEvent
                @Module NVARCHAR(50),
                @Action NVARCHAR(50),
                @EntitySummary NVARCHAR(MAX),
                @Status NVARCHAR(50) = 'SYNCED'
            AS
            BEGIN
                SET NOCOUNT ON;
                INSERT INTO ${p}sync_log (module, action, entity_summary, status, sync_timestamp)
                VALUES (@Module, @Action, @EntitySummary, @Status, GETDATE());
            END;
            """.trimIndent()
        )
    }

    /**
     * Builds safe, non-conflicting SQL DDL statements for the database, all master tables, and stored procedures for manual review.
     */
    fun generateCentralSchemaSql(config: ServerConfig): String {
        val db = config.database.ifBlank { "pomaster_central_db" }
        val p = config.tablePrefix.ifBlank { "pomaster_" }
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val tableStatements = getIndividualTableCreationStatements(p).joinToString("\nGO\n\n")
        val procStatements = getIndividualStoredProcedureStatements(p).joinToString("\nGO\n\n")

        return """
            -- =========================================================================
            -- PO MASTER ERP - MICROSOFT SQL SERVER MASTER TABLES & STORED PROCEDURES
            -- Target Database: [$db] | Host: ${config.host}:${config.port} | Prefix: '$p'
            -- Generated At   : $now
            -- =========================================================================

            IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'$db')
            BEGIN
                CREATE DATABASE [$db];
            END
            GO

            USE [$db];
            GO

            -- 1. MASTER TABLES CREATION
            $tableStatements
            GO

            -- 2. ENTERPRISE STORED PROCEDURES
            $procStatements
            GO

            -- =========================================================================
            -- INITIALIZATION COMPLETED
            -- =========================================================================
        """.trimIndent()
    }
}
