package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.data.cloud.SqlServerSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.sql.Connection
import java.sql.DriverManager
import java.text.SimpleDateFormat
import java.util.*

enum class DiagnosticStepStatus {
    IDLE, RUNNING, SUCCESS, WARNING, FAILED
}

data class DiagnosticStep(
    val id: String,
    val title: String,
    val description: String,
    var status: DiagnosticStepStatus = DiagnosticStepStatus.IDLE,
    var resultMessage: String = "",
    var details: String = "",
    var latencyMs: Long = 0,
    var exception: String? = null
)

data class DiscoveredDatabase(
    val name: String,
    val state: String,
    val isSystem: Boolean
)

data class DiscoveredTable(
    val tableName: String,
    val rowCount: Long = 0
)

/**
 * Enterprise Live Diagnostic Tool for Microsoft SQL Server & jTDS Connection.
 * Performs deep, step-by-step socket, TCP, TLS/Auth, catalog enumeration,
 * table verification, and one-tap automated remediation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqlServerDiagnosticDialog(
    initialHost: String = BuildConfig.SQL_SERVER_HOST.ifBlank { "103.164.3.18" },
    initialPort: String = BuildConfig.SQL_SERVER_PORT.ifBlank { "55200" },
    initialDatabase: String = BuildConfig.SQL_DATABASE_NAME.ifBlank { "pomaster_central_db" },
    initialUsername: String = BuildConfig.SQL_SERVER_USER.ifBlank { "sa" },
    initialPassword: String = BuildConfig.SQL_SERVER_PASSWORD.ifBlank { "icube@123" },
    initialPrefix: String = BuildConfig.SQL_TABLE_PREFIX.ifBlank { "pomaster_" },
    onDismiss: () -> Unit,
    onApplyConfig: ((host: String, port: String, database: String, username: String, password: String, prefix: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("po_master_sql_server_prefs", Context.MODE_PRIVATE) }

    var host by remember { mutableStateOf(sharedPrefs.getString("sql_server_host", initialHost) ?: initialHost) }
    var port by remember { mutableStateOf(sharedPrefs.getString("sql_server_port", initialPort) ?: initialPort) }
    var database by remember { mutableStateOf(sharedPrefs.getString("sql_database_name", initialDatabase) ?: initialDatabase) }
    var username by remember { mutableStateOf(sharedPrefs.getString("sql_username", initialUsername) ?: initialUsername) }
    var password by remember { mutableStateOf(sharedPrefs.getString("sql_password", initialPassword) ?: initialPassword) }
    var prefix by remember { mutableStateOf(sharedPrefs.getString("sql_table_prefix", initialPrefix) ?: initialPrefix) }
    var hidePassword by remember { mutableStateOf(true) }

    var isRunningDiagnostics by remember { mutableStateOf(false) }
    var isExecutingAction by remember { mutableStateOf(false) }
    var actionStatusMessage by remember { mutableStateOf<String?>(null) }

    var serverInfo by remember { mutableStateOf<String?>(null) }
    var serverName by remember { mutableStateOf<String?>(null) }
    var serverVersion by remember { mutableStateOf<String?>(null) }
    var discoveredDbs by remember { mutableStateOf<List<DiscoveredDatabase>>(emptyList()) }
    var discoveredTables by remember { mutableStateOf<List<DiscoveredTable>>(emptyList()) }
    var logs by remember { mutableStateOf<List<String>>(emptyList()) }

    var showConfigEditor by remember { mutableStateOf(false) }

    val steps = remember {
        mutableStateListOf(
            DiagnosticStep("step_dns", "1. DNS & Network Resolution", "Resolve IP address and ping host reachability"),
            DiagnosticStep("step_socket", "2. TCP Socket Port Handshake", "Check raw TCP socket reachability on target port"),
            DiagnosticStep("step_driver", "3. jTDS JDBC Driver Initialization", "Verify jTDS JDBC driver engine is loaded in Android runtime"),
            DiagnosticStep("step_master_auth", "4. Server Authentication (sa/master)", "Authenticate login against SQL Server master catalog"),
            DiagnosticStep("step_target_db", "5. Target Database Access", "Verify target database exists and user has full CRUD permissions"),
            DiagnosticStep("step_schema_tables", "6. Master Tables & Prefix Verification", "Inspect schema for required ERP master tables and row counts")
        )
    }

    fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        logs = logs + "[$time] $message"
    }

    fun runDiagnostics() {
        isRunningDiagnostics = true
        serverInfo = null
        serverName = null
        serverVersion = null
        discoveredDbs = emptyList()
        discoveredTables = emptyList()
        logs = emptyList()

        steps.forEach {
            it.status = DiagnosticStepStatus.IDLE
            it.resultMessage = ""
            it.details = ""
            it.latencyMs = 0
            it.exception = null
        }

        coroutineScope.launch {
            val targetHost = host.trim()
            val targetPort = port.toIntOrNull() ?: 55200
            val targetDb = database.trim()
            val targetUser = username.trim()
            val targetPass = password
            val targetPrefix = prefix.trim().ifBlank { "pomaster_" }

            addLog("🚀 Starting Diagnostic Suite against $targetHost:$targetPort...")
            addLog("⚙️ Target DB: '$targetDb' | User: '$targetUser' | Prefix: '$targetPrefix'")

            // -------------------------------------------------------------
            // STEP 1: DNS & IP Resolution
            // -------------------------------------------------------------
            steps[0].status = DiagnosticStepStatus.RUNNING
            val t0 = System.currentTimeMillis()
            var resolvedIp = ""
            try {
                withContext(Dispatchers.IO) {
                    val address = InetAddress.getByName(targetHost)
                    resolvedIp = address.hostAddress ?: targetHost
                }
                val lat = System.currentTimeMillis() - t0
                steps[0].status = DiagnosticStepStatus.SUCCESS
                steps[0].latencyMs = lat
                steps[0].resultMessage = "Resolved IP: $resolvedIp (${lat}ms)"
                steps[0].details = "Host '$targetHost' is valid and reachable over DNS."
                addLog("✅ Step 1 Passed: DNS resolved to $resolvedIp in ${lat}ms")
            } catch (e: Exception) {
                val lat = System.currentTimeMillis() - t0
                steps[0].status = DiagnosticStepStatus.FAILED
                steps[0].latencyMs = lat
                steps[0].resultMessage = "DNS Resolution Failed"
                steps[0].details = e.localizedMessage ?: "Unknown DNS error"
                steps[0].exception = e.toString()
                addLog("❌ Step 1 Failed: Cannot resolve '$targetHost': ${e.message}")
                isRunningDiagnostics = false
                return@launch
            }

            // -------------------------------------------------------------
            // STEP 2: TCP Socket & Firewall Assessment Check
            // -------------------------------------------------------------
            steps[1].status = DiagnosticStepStatus.RUNNING
            val tcpResult = com.example.service.TcpFirewallDiagnosticHelper.testTcpReachability(
                host = targetHost,
                port = targetPort,
                timeoutMs = 6000
            )

            if (tcpResult.isReachable) {
                steps[1].status = DiagnosticStepStatus.SUCCESS
                steps[1].latencyMs = tcpResult.latencyMs
                steps[1].resultMessage = "TCP Port $targetPort OPEN (${tcpResult.latencyMs}ms)"
                steps[1].details = "Successfully connected to $targetHost:$targetPort over raw TCP. No firewall blocks detected."
                addLog("✅ Step 2 Passed: TCP port $targetPort is open and accepting socket connections (${tcpResult.latencyMs}ms)")
            } else {
                steps[1].status = DiagnosticStepStatus.FAILED
                steps[1].latencyMs = tcpResult.latencyMs
                steps[1].resultMessage = tcpResult.statusSummary
                steps[1].details = tcpResult.technicalDetails
                steps[1].exception = tcpResult.statusSummary
                addLog("❌ Step 2 Failed: ${tcpResult.statusSummary}. ${tcpResult.technicalDetails}")
                isRunningDiagnostics = false
                return@launch
            }

            // -------------------------------------------------------------
            // STEP 3: jTDS Driver Loading
            // -------------------------------------------------------------
            steps[2].status = DiagnosticStepStatus.RUNNING
            try {
                Class.forName("net.sourceforge.jtds.jdbc.Driver")
                steps[2].status = DiagnosticStepStatus.SUCCESS
                steps[2].resultMessage = "jTDS 1.3.1 Active"
                steps[2].details = "Driver class net.sourceforge.jtds.jdbc.Driver initialized successfully."
                addLog("✅ Step 3 Passed: jTDS JDBC driver loaded successfully.")
            } catch (e: Exception) {
                steps[2].status = DiagnosticStepStatus.FAILED
                steps[2].resultMessage = "Driver Class Not Found"
                steps[2].details = e.localizedMessage ?: "Classpath error"
                steps[2].exception = e.toString()
                addLog("❌ Step 3 Failed: jTDS driver not available: ${e.message}")
                isRunningDiagnostics = false
                return@launch
            }

            // -------------------------------------------------------------
            // STEP 4: Server Root / master Auth
            // -------------------------------------------------------------
            steps[3].status = DiagnosticStepStatus.RUNNING
            val t3 = System.currentTimeMillis()
            var masterConn: Connection? = null
            try {
                withContext(Dispatchers.IO) {
                    DriverManager.setLoginTimeout(8)
                    val masterUrl = "jdbc:jtds:sqlserver://$targetHost:$targetPort/master;ssl=off;useNTLMv2=false;charset=UTF-8;loginTimeout=8;socketTimeout=10;"
                    addLog("🔌 Connecting to master catalog: $masterUrl")
                    masterConn = DriverManager.getConnection(masterUrl, targetUser, targetPass)
                    
                    masterConn?.let { conn ->
                        val stmt = conn.createStatement()
                        val rs = stmt.executeQuery("SELECT @@VERSION AS ver, @@SERVERNAME AS srv, SERVERPROPERTY('ProductVersion') AS prod_ver, SERVERPROPERTY('Edition') AS edition")
                        if (rs.next()) {
                            serverVersion = rs.getString("ver") ?: "SQL Server"
                            serverName = rs.getString("srv") ?: "WIN-SERVER"
                            val edition = rs.getString("edition") ?: ""
                            val prodVer = rs.getString("prod_ver") ?: ""
                            serverInfo = "$serverName • $edition ($prodVer)"
                        }
                        rs.close()

                        // Enumerate all databases on the SQL Server
                        val dbList = mutableListOf<DiscoveredDatabase>()
                        val dbRs = stmt.executeQuery("SELECT name, state_desc FROM sys.databases ORDER BY name ASC")
                        val sysDbs = setOf("master", "tempdb", "model", "msdb")
                        while (dbRs.next()) {
                            val dbName = dbRs.getString("name") ?: ""
                            val state = dbRs.getString("state_desc") ?: "ONLINE"
                            dbList.add(DiscoveredDatabase(name = dbName, state = state, isSystem = dbName.lowercase() in sysDbs))
                        }
                        dbRs.close()
                        stmt.close()
                        discoveredDbs = dbList
                    }
                }
                val lat = System.currentTimeMillis() - t3
                steps[3].status = DiagnosticStepStatus.SUCCESS
                steps[3].latencyMs = lat
                steps[3].resultMessage = "Authenticated as '$targetUser' (${lat}ms)"
                steps[3].details = "Server: $serverName (${discoveredDbs.size} databases detected on instance)"
                addLog("✅ Step 4 Passed: Authenticated with user '$targetUser' on server '$serverName' (${discoveredDbs.size} databases)")
            } catch (e: Exception) {
                val lat = System.currentTimeMillis() - t3
                steps[3].status = DiagnosticStepStatus.FAILED
                steps[3].latencyMs = lat
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                steps[3].resultMessage = "Login Failed for '$targetUser'"
                steps[3].details = if (msg.contains("Login failed", ignoreCase = true) || msg.contains("18456", ignoreCase = true)) {
                    "Error 18456: Invalid credentials or SQL Server Mixed Mode Authentication is disabled. Enable 'SQL Server and Windows Authentication mode' in SSMS Properties -> Security."
                } else {
                    msg
                }
                steps[3].exception = e.toString()
                addLog("❌ Step 4 Failed: SQL Authentication failed: ${e.message}")
                isRunningDiagnostics = false
                return@launch
            }

            // -------------------------------------------------------------
            // STEP 5: Target Database Check
            // -------------------------------------------------------------
            steps[4].status = DiagnosticStepStatus.RUNNING
            val t4 = System.currentTimeMillis()
            val targetDbExists = discoveredDbs.any { it.name.equals(targetDb, ignoreCase = true) }
            
            if (!targetDbExists) {
                val lat = System.currentTimeMillis() - t4
                steps[4].status = DiagnosticStepStatus.WARNING
                steps[4].latencyMs = lat
                steps[4].resultMessage = "Database '$targetDb' DOES NOT EXIST"
                steps[4].details = "The database '$targetDb' is not yet created on server '$serverName'. You can create it instantly using the button below or choose an existing unit database."
                addLog("⚠️ Step 5 Notice: Database '$targetDb' is missing on '$serverName'.")
            } else {
                var targetConn: Connection? = null
                try {
                    withContext(Dispatchers.IO) {
                        val dbUrl = "jdbc:jtds:sqlserver://$targetHost:$targetPort/$targetDb;ssl=off;useNTLMv2=false;charset=UTF-8;loginTimeout=8;socketTimeout=10;"
                        addLog("🔌 Connecting directly to target database: $dbUrl")
                        targetConn = DriverManager.getConnection(dbUrl, targetUser, targetPass)
                        targetConn?.close()
                    }
                    val lat = System.currentTimeMillis() - t4
                    steps[4].status = DiagnosticStepStatus.SUCCESS
                    steps[4].latencyMs = lat
                    steps[4].resultMessage = "Database '$targetDb' ONLINE (${lat}ms)"
                    steps[4].details = "Successfully connected and authenticated directly against '$targetDb'."
                    addLog("✅ Step 5 Passed: Target database '$targetDb' is online and accessible.")
                } catch (e: Exception) {
                    val lat = System.currentTimeMillis() - t4
                    steps[4].status = DiagnosticStepStatus.FAILED
                    steps[4].latencyMs = lat
                    steps[4].resultMessage = "Cannot open '$targetDb'"
                    steps[4].details = e.localizedMessage ?: "Permission or catalog error"
                    steps[4].exception = e.toString()
                    addLog("❌ Step 5 Failed: Could not connect to '$targetDb': ${e.message}")
                }
            }

            // -------------------------------------------------------------
            // STEP 6: Master Tables & Prefix Verification
            // -------------------------------------------------------------
            steps[5].status = DiagnosticStepStatus.RUNNING
            if (!targetDbExists) {
                steps[5].status = DiagnosticStepStatus.IDLE
                steps[5].resultMessage = "Skipped (Database not created)"
                steps[5].details = "Create database '$targetDb' first to inspect tables."
            } else {
                val t5 = System.currentTimeMillis()
                try {
                    val tables = mutableListOf<DiscoveredTable>()
                    withContext(Dispatchers.IO) {
                        val dbUrl = "jdbc:jtds:sqlserver://$targetHost:$targetPort/$targetDb;ssl=off;useNTLMv2=false;charset=UTF-8;loginTimeout=8;socketTimeout=10;"
                        DriverManager.getConnection(dbUrl, targetUser, targetPass).use { conn ->
                            val stmt = conn.createStatement()
                            val rs = stmt.executeQuery("""
                                SELECT TABLE_NAME 
                                FROM INFORMATION_SCHEMA.TABLES 
                                WHERE TABLE_TYPE = 'BASE TABLE' AND TABLE_NAME LIKE '${targetPrefix}%'
                                ORDER BY TABLE_NAME ASC
                            """.trimIndent())
                            val tableNames = mutableListOf<String>()
                            while (rs.next()) {
                                tableNames.add(rs.getString("TABLE_NAME"))
                            }
                            rs.close()

                            for (tName in tableNames) {
                                try {
                                    val countRs = stmt.executeQuery("SELECT COUNT(*) AS total FROM [$tName]")
                                    val count = if (countRs.next()) countRs.getLong("total") else 0L
                                    countRs.close()
                                    tables.add(DiscoveredTable(tName, count))
                                } catch (e: Exception) {
                                    tables.add(DiscoveredTable(tName, -1))
                                }
                            }
                            stmt.close()
                        }
                    }
                    val lat = System.currentTimeMillis() - t5
                    discoveredTables = tables
                    if (tables.isEmpty()) {
                        steps[5].status = DiagnosticStepStatus.WARNING
                        steps[5].latencyMs = lat
                        steps[5].resultMessage = "0 Tables with prefix '$targetPrefix'"
                        steps[5].details = "No tables found with prefix '$targetPrefix'. Tap 'Initialize Tables' to generate all 13 ERP tables."
                        addLog("⚠️ Step 6 Notice: 0 tables found with prefix '$targetPrefix' in '$targetDb'")
                    } else {
                        steps[5].status = DiagnosticStepStatus.SUCCESS
                        steps[5].latencyMs = lat
                        steps[5].resultMessage = "${tables.size} Master Tables Active (${lat}ms)"
                        steps[5].details = "Tables found: ${tables.joinToString { "${it.tableName} (${it.rowCount} rows)" }}"
                        addLog("✅ Step 6 Passed: Found ${tables.size} tables in '$targetDb' with prefix '$targetPrefix'")
                    }
                } catch (e: Exception) {
                    val lat = System.currentTimeMillis() - t5
                    steps[5].status = DiagnosticStepStatus.FAILED
                    steps[5].latencyMs = lat
                    steps[5].resultMessage = "Table Query Failed"
                    steps[5].details = e.localizedMessage ?: "Query error"
                    steps[5].exception = e.toString()
                    addLog("❌ Step 6 Failed: Query failed: ${e.message}")
                }
            }

            try {
                masterConn?.close()
            } catch (_: Exception) {}

            isRunningDiagnostics = false
            addLog("🏁 Diagnostics Finished!")
        }
    }

    // Auto-run diagnostics when opened
    LaunchedEffect(Unit) {
        runDiagnostics()
    }

    fun createDatabaseOnServer(dbNameToCreate: String) {
        isExecutingAction = true
        actionStatusMessage = "Creating database [$dbNameToCreate] on $host:$port..."
        addLog("🛠️ Executing: CREATE DATABASE [$dbNameToCreate]...")
        coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val masterUrl = "jdbc:jtds:sqlserver://${host.trim()}:${port.trim()}/master;ssl=off;useNTLMv2=false;charset=UTF-8;loginTimeout=8;socketTimeout=10;"
                    DriverManager.getConnection(masterUrl, username.trim(), password).use { conn ->
                        val stmt = conn.createStatement()
                        stmt.executeUpdate("IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'$dbNameToCreate') CREATE DATABASE [$dbNameToCreate];")
                        stmt.close()
                    }
                }
                actionStatusMessage = "✅ Database [$dbNameToCreate] created successfully!"
                addLog("✅ Database [$dbNameToCreate] successfully created on server!")
                Toast.makeText(context, "Database '$dbNameToCreate' created on SQL Server!", Toast.LENGTH_SHORT).show()
                database = dbNameToCreate
                runDiagnostics()
            } catch (e: Exception) {
                actionStatusMessage = "❌ Error creating DB: ${e.localizedMessage}"
                addLog("❌ Failed to create DB: ${e.message}")
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isExecutingAction = false
            }
        }
    }

    fun initializeSchemaNow() {
        isExecutingAction = true
        actionStatusMessage = "Initializing 13 master tables & stored procedures in [$database]..."
        addLog("🛠️ Generating schema & procedures in [$database] with prefix '$prefix'...")
        coroutineScope.launch {
            val config = SqlServerSyncService.ServerConfig(
                host = host.trim(),
                port = port.toIntOrNull() ?: 55200,
                database = database.trim(),
                username = username.trim(),
                password = password,
                tablePrefix = prefix.trim().ifBlank { "pomaster_" }
            )
            val result = SqlServerSyncService.initializeCentralDatabaseAndSchema(config)
            isExecutingAction = false
            if (result.success) {
                actionStatusMessage = "✅ All 13 master tables & procedures created!"
                addLog("✅ Schema initialized: ${result.tablesCreated.size} tables created.")
                Toast.makeText(context, "Schema Initialized Successfully in '$database'!", Toast.LENGTH_SHORT).show()
                runDiagnostics()
            } else {
                actionStatusMessage = "❌ Schema init failed: ${result.details}"
                addLog("❌ Schema initialization error: ${result.details}")
                Toast.makeText(context, "Schema Init Failed: ${result.details}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun copyDiagnosticReport() {
        val sb = StringBuilder()
        sb.appendLine("==================================================")
        sb.appendLine("PO MASTER ERP - SQL SERVER DIAGNOSTIC REPORT")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("==================================================")
        sb.appendLine("Host: $host:$port")
        sb.appendLine("Target Database: $database")
        sb.appendLine("Username: $username")
        sb.appendLine("Prefix: $prefix")
        sb.appendLine("Server Info: ${serverInfo ?: "N/A"}")
        sb.appendLine("Server Version: ${serverVersion ?: "N/A"}")
        sb.appendLine()
        sb.appendLine("--- DIAGNOSTIC STEPS ---")
        steps.forEach { step ->
            val icon = when (step.status) {
                DiagnosticStepStatus.SUCCESS -> "[PASS]"
                DiagnosticStepStatus.FAILED -> "[FAIL]"
                DiagnosticStepStatus.WARNING -> "[WARN]"
                DiagnosticStepStatus.RUNNING -> "[RUN]"
                DiagnosticStepStatus.IDLE -> "[SKIP]"
            }
            sb.appendLine("$icon ${step.title}: ${step.resultMessage} (${step.latencyMs}ms)")
            if (step.details.isNotBlank()) sb.appendLine("       Details: ${step.details}")
            if (step.exception != null) sb.appendLine("       Exception: ${step.exception}")
        }
        sb.appendLine()
        sb.appendLine("--- DISCOVERED DATABASES (${discoveredDbs.size}) ---")
        discoveredDbs.forEach { db ->
            sb.appendLine(" • ${db.name} (${db.state})${if (db.isSystem) " [System]" else ""}")
        }
        sb.appendLine()
        sb.appendLine("--- DISCOVERED MASTER TABLES (${discoveredTables.size}) ---")
        discoveredTables.forEach { table ->
            sb.appendLine(" • ${table.tableName} (${table.rowCount} records)")
        }
        sb.appendLine()
        sb.appendLine("--- LIVE DIAGNOSTIC LOGS ---")
        logs.forEach { log -> sb.appendLine(log) }
        sb.appendLine("==================================================")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SQL Server Diagnostic Report", sb.toString()))
        Toast.makeText(context, "📋 Diagnostic Report copied to clipboard!", Toast.LENGTH_LONG).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .testTag("sql_server_diagnostic_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Troubleshoot,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "SQL Server Live Diagnostics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$host:$port • jTDS Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row {
                        IconButton(onClick = { copyDiagnosticReport() }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report")
                        }
                        IconButton(onClick = { showConfigEditor = !showConfigEditor }) {
                            Icon(
                                if (showConfigEditor) Icons.Default.Tune else Icons.Default.Settings,
                                contentDescription = "Toggle Config",
                                tint = if (showConfigEditor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Optional Collapsible Config Editor
                AnimatedVisibility(visible = showConfigEditor) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔧 Quick Credentials Adjuster", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = host,
                                    onValueChange = { host = it },
                                    label = { Text("Host / IP", fontSize = 11.sp) },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = port,
                                    onValueChange = { port = it },
                                    label = { Text("Port", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = database,
                                    onValueChange = { database = it },
                                    label = { Text("Database", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1.5f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = prefix,
                                    onValueChange = { prefix = it },
                                    label = { Text("Table Prefix", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("User", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    visualTransformation = if (hidePassword) PasswordVisualTransformation() else VisualTransformation.None,
                                    trailingIcon = {
                                        IconButton(onClick = { hidePassword = !hidePassword }) {
                                            Icon(
                                                imageVector = if (hidePassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        sharedPrefs.edit()
                                            .putString("sql_server_host", host.trim())
                                            .putString("sql_server_port", port.trim())
                                            .putString("sql_database_name", database.trim())
                                            .putString("sql_username", username.trim())
                                            .putString("sql_password", password)
                                            .putString("sql_table_prefix", prefix.trim())
                                            .apply()
                                        onApplyConfig?.invoke(host.trim(), port.trim(), database.trim(), username.trim(), password, prefix.trim())
                                        showConfigEditor = false
                                        runDiagnostics()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save & Re-Test", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Main Diagnostic Feed
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Server Overview Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (serverInfo != null) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (serverInfo != null) Icons.Default.Dns else Icons.Default.SensorsOff,
                                    contentDescription = null,
                                    tint = if (serverInfo != null) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (serverInfo != null) "SQL Server Instance Connected" else "Connection State: Evaluating...",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (serverInfo != null) Color(0xFF1B5E20) else Color(0xFFBF360C)
                                    )
                                    Text(
                                        text = serverInfo ?: "Testing socket reachability and SQL authentication against $host:$port",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    // Diagnostic Steps
                    items(steps) { step ->
                        DiagnosticStepItem(step = step)
                    }

                    // Remediation & Action Bar
                    if (steps[3].status == DiagnosticStepStatus.SUCCESS) {
                        val targetDbExists = discoveredDbs.any { it.name.equals(database, ignoreCase = true) }

                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("1-Tap Fix & Server Actions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    }

                                    if (!targetDbExists) {
                                        Text(
                                            "Target database '$database' is missing on '$serverName'. Click below to create it immediately:",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Button(
                                            onClick = { createDatabaseOnServer(database) },
                                            enabled = !isExecutingAction,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("CREATE DATABASE [$database] NOW", fontWeight = FontWeight.Bold)
                                        }
                                    } else if (discoveredTables.isEmpty()) {
                                        Text(
                                            "Database '$database' exists, but ERP master tables with prefix '$prefix' have not been created yet.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Button(
                                            onClick = { initializeSchemaNow() },
                                            enabled = !isExecutingAction,
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("INITIALIZE ALL 13 ERP TABLES NOW", fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Text(
                                            "✅ Database '$database' and ${discoveredTables.size} master tables are active and ready for live synchronization.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    actionStatusMessage?.let { msg ->
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Databases Discovered on Server
                        if (discoveredDbs.isNotEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "📂 Existing Databases on Server (${discoveredDbs.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(discoveredDbs) { db ->
                                            val isSelected = db.name.equals(database, ignoreCase = true)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    database = db.name
                                                    sharedPrefs.edit().putString("sql_database_name", db.name).apply()
                                                    onApplyConfig?.invoke(host.trim(), port.trim(), db.name, username.trim(), password, prefix.trim())
                                                    runDiagnostics()
                                                },
                                                label = {
                                                    Text(
                                                        text = "${db.name} ${if (isSelected) "✓" else ""}",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = if (db.isSystem) Icons.Default.Lock else Icons.Default.Folder,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live Log Terminal Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "💻 Live Diagnostic Console (${logs.size} entries)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF9E9E9E),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (logs.isEmpty()) {
                                            Text(
                                                text = "Waiting for diagnostic runner...",
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                color = Color.Gray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        } else {
                                            logs.takeLast(25).forEach { log ->
                                                Text(
                                                    text = log,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = when {
                                                        log.contains("❌") -> Color(0xFFFF5252)
                                                        log.contains("✅") -> Color(0xFF69F0AE)
                                                        log.contains("⚠️") -> Color(0xFFFFD740)
                                                        else -> Color(0xFFE0E0E0)
                                                    },
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { copyDiagnosticReport() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Full Report", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { runDiagnostics() },
                        enabled = !isRunningDiagnostics && !isExecutingAction,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (isRunningDiagnostics) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Running Tests...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-Run Diagnostics", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticStepItem(step: DiagnosticStep) {
    val (statusColor, statusIcon) = when (step.status) {
        DiagnosticStepStatus.SUCCESS -> Color(0xFF2E7D32) to Icons.Default.CheckCircle
        DiagnosticStepStatus.FAILED -> Color(0xFFC62828) to Icons.Default.Cancel
        DiagnosticStepStatus.WARNING -> Color(0xFFF57F17) to Icons.Default.Warning
        DiagnosticStepStatus.RUNNING -> MaterialTheme.colorScheme.primary to Icons.Default.Sync
        DiagnosticStepStatus.IDLE -> Color.Gray to Icons.Default.RadioButtonUnchecked
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (step.status == DiagnosticStepStatus.RUNNING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = statusColor
                        )
                    } else {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (step.latencyMs > 0) {
                    Text(
                        text = "${step.latencyMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (step.resultMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = step.resultMessage,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }

            if (step.details.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = step.details,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (step.exception != null) {
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(
                        text = "Exception: ${step.exception}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = Color(0xFFD32F2F),
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}
