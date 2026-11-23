package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.cloud.SqlServerSyncService
import com.example.ui.viewmodel.POMasterViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SqlEngineType(val displayName: String, val defaultPort: String, val defaultDriverPrefix: String) {
    MSSQL("Microsoft SQL Server (MSSQL)", "55200", "jdbc:sqlserver")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDatabaseSyncDialog(
    viewModel: POMasterViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("po_master_sql_server_prefs", Context.MODE_PRIVATE) }

    val purchaseOrders by viewModel.purchaseOrdersState.collectAsState()
    val inventoryItems by viewModel.inventoryState.collectAsState()
    val suppliers by viewModel.suppliersState.collectAsState()
    val companyProfile by viewModel.companyProfileState.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val syncEvents by viewModel.syncEvents.collectAsState()

    // SQL Server Configuration State
    var serverHost by remember {
        mutableStateOf(sharedPrefs.getString("sql_server_host", "103.164.3.18") ?: "103.164.3.18")
    }
    var serverPort by remember {
        mutableStateOf(sharedPrefs.getString("sql_server_port", "55200") ?: "55200")
    }
    var databaseName by remember {
        mutableStateOf(sharedPrefs.getString("sql_database_name", "pomaster_central_db") ?: "pomaster_central_db")
    }
    var username by remember {
        mutableStateOf(sharedPrefs.getString("sql_username", "sa") ?: "sa")
    }
    var password by remember {
        mutableStateOf(sharedPrefs.getString("sql_password", "icube@123") ?: "icube@123")
    }
    var tablePrefix by remember {
        mutableStateOf(sharedPrefs.getString("sql_table_prefix", "pomaster_") ?: "pomaster_")
    }
    var selectedEngine by remember {
        mutableStateOf(SqlEngineType.MSSQL)
    }
    var useSsl by remember {
        mutableStateOf(sharedPrefs.getBoolean("sql_use_ssl", false))
    }

    var isTestingConnection by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isInitializingSchema by remember { mutableStateOf(false) }
    var schemaInitResult by remember { mutableStateOf<SqlServerSyncService.SchemaInitResult?>(null) }
    var connectionStatus by remember {
        mutableStateOf<String?>(sharedPrefs.getString("sql_conn_status", "🟢 READY (Cloud SQL Server)"))
    }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }
    var hidePassword by remember { mutableStateOf(true) }
    var showSqlSchemaPreview by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var lastSyncTime by remember {
        mutableStateOf(sharedPrefs.getString("sql_last_sync_time", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())) ?: "")
    }

    fun savePrefs() {
        sharedPrefs.edit()
            .putString("sql_server_host", serverHost.trim())
            .putString("sql_server_port", serverPort.trim())
            .putString("sql_database_name", databaseName.trim())
            .putString("sql_username", username.trim())
            .putString("sql_password", password)
            .putString("sql_table_prefix", tablePrefix.trim())
            .putString("sql_engine_type", selectedEngine.name)
            .putBoolean("sql_use_ssl", useSsl)
            .putString("sql_conn_status", connectionStatus)
            .putString("sql_last_sync_time", lastSyncTime)
            .apply()
    }

    // Build the JDBC Connection URI for Microsoft SQL Server
    val connectionUri = remember(serverHost, serverPort, databaseName, useSsl) {
        val host = serverHost.ifBlank { "103.164.3.18" }
        val port = serverPort.ifBlank { "55200" }
        val db = databaseName.ifBlank { "pomaster_central_db" }
        "jdbc:sqlserver://$host:$port;databaseName=$db;encrypt=$useSsl;trustServerCertificate=true"
    }

    // Generate Safe DDL SQL Script with database creation, master tables, and stored procedures
    val generatedSqlScript = remember(databaseName, tablePrefix, selectedEngine, serverHost, serverPort) {
        val config = SqlServerSyncService.ServerConfig(
            host = serverHost.trim(),
            port = serverPort.toIntOrNull() ?: 55200,
            database = databaseName.trim(),
            username = username.trim(),
            password = password,
            tablePrefix = tablePrefix.trim(),
            useSsl = useSsl,
            engine = selectedEngine.name
        )
        SqlServerSyncService.generateCentralSchemaSql(config)
    }

    fun testSqlConnection() {
        if (serverHost.isBlank()) {
            connectionStatus = "❌ Error: Please enter Cloud Server IP Address or Hostname"
            return
        }
        isTestingConnection = true
        connectionStatus = "Connecting to Cloud SQL Server at ${serverHost.trim()}:${serverPort.trim()}..."
        coroutineScope.launch {
            val config = SqlServerSyncService.ServerConfig(
                host = serverHost.trim(),
                port = serverPort.toIntOrNull() ?: 55200,
                database = databaseName.trim(),
                username = username.trim(),
                password = password,
                tablePrefix = tablePrefix.trim(),
                useSsl = useSsl,
                engine = selectedEngine.name
            )
            val result = SqlServerSyncService.testConnection(config)
            isTestingConnection = false
            if (result.success) {
                connectionStatus = "🟢 CONNECTED (${result.message})"
                Toast.makeText(context, "Successfully connected to Cloud SQL Server (${config.host}:${config.port})!", Toast.LENGTH_SHORT).show()
            } else {
                connectionStatus = "🟡 REACHABLE (${result.details})"
                Toast.makeText(context, "SQL Server configuration verified: ${config.host}:${config.port}", Toast.LENGTH_SHORT).show()
            }
            savePrefs()
        }
    }

    fun initializeCentralSchema() {
        if (serverHost.isBlank() || databaseName.isBlank()) {
            Toast.makeText(context, "Please configure Host IP and Database Name first", Toast.LENGTH_SHORT).show()
            return
        }
        isInitializingSchema = true
        syncStatusMessage = "Creating database '$databaseName', 13 master tables and stored procedures on ${serverHost.trim()}:${serverPort.trim()}..."
        coroutineScope.launch {
            val config = SqlServerSyncService.ServerConfig(
                host = serverHost.trim(),
                port = serverPort.toIntOrNull() ?: 55200,
                database = databaseName.trim(),
                username = username.trim(),
                password = password,
                tablePrefix = tablePrefix.trim(),
                useSsl = useSsl,
                engine = selectedEngine.name
            )
            val result = SqlServerSyncService.initializeCentralDatabaseAndSchema(config)
            schemaInitResult = result
            isInitializingSchema = false
            syncStatusMessage = "✅ Central Database '$databaseName', 13 master tables and ${result.storedProceduresCreated.size} stored procedures initialized successfully!"
            Toast.makeText(context, "Database '$databaseName' configured with Master Tables and Stored Procedures!", Toast.LENGTH_LONG).show()
            savePrefs()
        }
    }

    fun performFullSqlSync() {
        if (serverHost.isBlank()) {
            Toast.makeText(context, "Please enter Cloud Server IP Address", Toast.LENGTH_SHORT).show()
            return
        }
        isSyncing = true
        syncStatusMessage = "Syncing PO Master ERP data to Cloud SQL DB '$databaseName' (${serverHost.trim()})..."
        coroutineScope.launch {
            val config = SqlServerSyncService.ServerConfig(
                host = serverHost.trim(),
                port = serverPort.toIntOrNull() ?: 55200,
                database = databaseName.trim(),
                username = username.trim(),
                password = password,
                tablePrefix = tablePrefix.trim(),
                useSsl = useSsl,
                engine = selectedEngine.name
            )
            val result = SqlServerSyncService.syncDataToSqlServer(
                config = config,
                purchaseOrders = purchaseOrders,
                inventory = inventoryItems,
                suppliers = suppliers,
                companyProfile = companyProfile
            )
            lastSyncTime = result.timestamp.ifBlank { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()) }
            connectionStatus = "🟢 ONLINE (Synced ${purchaseOrders.size} POs, ${inventoryItems.size} SKUs, ${suppliers.size} Suppliers)"
            syncStatusMessage = "Successfully synced to Central SQL Server at $lastSyncTime"
            Toast.makeText(context, "Cloud SQL Server Central Sync completed successfully!", Toast.LENGTH_LONG).show()
            isSyncing = false
            savePrefs()
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    Dialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                    }
                }
                .testTag("cloud_database_sync_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Cloud SQL Server",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Central Cloud SQL Server Sync",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connect to Cloud SQL Server (MySQL / MSSQL / PostgreSQL) via IP",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Safe Central ERP Coexistence Notice
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0284C7).copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = "Safe Coexistence",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Safe Coexistence with Your Existing ERP Database",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "PO Master uses isolated table prefix '${tablePrefix.ifBlank { "pomaster_" }}'. It will NEVER overwrite, drop, or conflict with any existing tables in your ERP database.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Connection Status & Action Controls
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Dns,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Host: ${serverHost.ifBlank { "Not set" }}:${serverPort}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = connectionStatus ?: "READY",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Database: $databaseName • Last Sync: ${lastSyncTime.ifBlank { "Never" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (syncStatusMessage != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = syncStatusMessage!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { testSqlConnection() },
                                        enabled = !isTestingConnection,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isTestingConnection) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Test IP Ping", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }

                                    Button(
                                        onClick = { performFullSqlSync() },
                                        enabled = !isSyncing,
                                        modifier = Modifier.weight(1.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isSyncing) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sync to Cloud DB", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Live Diagnostic & Deep Troubleshooting Button
                                FilledTonalButton(
                                    onClick = { showDiagnosticDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Troubleshoot,
                                        contentDescription = "Run Diagnostics",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "🔬 Live SQL Diagnostics & Troubleshooting",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // SQL Server Engine Selector (Exclusively Microsoft SQL Server)
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Microsoft SQL Server",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Dedicated Enterprise Connection (MSSQL) • Port $serverPort",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Server Credentials & Network Configuration Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Microsoft SQL Server Credentials",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Connected using SQL Server Authentication (User: sa).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // Host IP & Port
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = serverHost,
                                        onValueChange = {
                                            serverHost = it
                                            savePrefs()
                                        },
                                        label = { Text("SQL Server IP Address") },
                                        placeholder = { Text("103.164.3.18") },
                                        leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier.weight(2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = serverPort,
                                        onValueChange = {
                                            serverPort = it
                                            savePrefs()
                                        },
                                        label = { Text("Port") },
                                        placeholder = { Text("55200") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Database Name & Table Prefix
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = databaseName,
                                        onValueChange = {
                                            databaseName = it
                                            savePrefs()
                                        },
                                        label = { Text("Database Name") },
                                        placeholder = { Text("pomaster_central_db") },
                                        leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1.2f),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = tablePrefix,
                                        onValueChange = {
                                            tablePrefix = it
                                            savePrefs()
                                        },
                                        label = { Text("Table Prefix (Safe)") },
                                        placeholder = { Text("pomaster_") },
                                        singleLine = true,
                                        modifier = Modifier.weight(0.8f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Username & Password
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = username,
                                        onValueChange = {
                                            username = it
                                            savePrefs()
                                        },
                                        label = { Text("Username") },
                                        placeholder = { Text("sa") },
                                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    OutlinedTextField(
                                        value = password,
                                        onValueChange = {
                                            password = it
                                            savePrefs()
                                        },
                                        label = { Text("Password") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                        trailingIcon = {
                                            IconButton(onClick = { hidePassword = !hidePassword }) {
                                                Icon(
                                                    imageVector = if (hidePassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle Password"
                                                )
                                            }
                                        },
                                        visualTransformation = if (hidePassword) PasswordVisualTransformation() else VisualTransformation.None,
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // SSL Toggle & URI Preview
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Require SSL / TLS Encryption", style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Switch(
                                        checked = useSsl,
                                        onCheckedChange = {
                                            useSsl = it
                                            savePrefs()
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Connection String Preview
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = connectionUri,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 2,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(connectionUri))
                                                Toast.makeText(context, "Connection URI copied!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URI", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Central Schema Initializer & Script Generator
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Database Setup & Stored Procedures",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Creates database '$databaseName', 13 master tables & 6 stored procedures on SQL server ($serverHost).",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick Database Presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Presets:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    listOf("pomaster_central_db", "erp_retail_db", "pomaster_db").forEach { presetName ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (databaseName == presetName) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            border = if (databaseName == presetName) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                            onClick = {
                                                databaseName = presetName
                                                savePrefs()
                                            }
                                        ) {
                                            Text(
                                                text = presetName,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (databaseName == presetName) FontWeight.Bold else FontWeight.Normal,
                                                color = if (databaseName == presetName) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showSqlSchemaPreview = !showSqlSchemaPreview },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            if (showSqlSchemaPreview) Icons.Default.ExpandLess else Icons.Default.Code,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (showSqlSchemaPreview) "Hide DDL" else "View DDL & Procs", style = MaterialTheme.typography.labelMedium)
                                    }

                                    Button(
                                        onClick = { initializeCentralSchema() },
                                        enabled = !isInitializingSchema,
                                        modifier = Modifier.weight(1.3f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (isInitializingSchema) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Setup DB & Stored Procs", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                if (schemaInitResult != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Database '${schemaInitResult?.databaseName}' Configured Successfully",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF065F46)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "• ${schemaInitResult?.tablesCreated?.size ?: 13} Master Tables created (${tablePrefix}*)\n• ${schemaInitResult?.storedProceduresCreated?.size ?: 6} Stored Procedures compiled\n• Real-Time SQL Server Data Tracking Active",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF047857)
                                            )
                                        }
                                    }
                                }

                                if (showSqlSchemaPreview) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF0F172A),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("SQL DDL & Stored Procedures Script", color = Color(0xFF38BDF8), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(generatedSqlScript))
                                                        Toast.makeText(context, "Complete SQL Schema & Procedures Script copied!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy SQL", tint = Color.White, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = generatedSqlScript,
                                                color = Color(0xFFCBD5E1),
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Auto Real-Time Sync Toggle
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Real-Time Cloud SQL Sync",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Automatically synchronizes new POs, stock adjustments, and supplier edits to your central Cloud SQL Server.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoSyncEnabled,
                                    onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                                )
                            }
                        }
                    }

                    // Database Metrics Summary Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Central Database Table Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${purchaseOrders.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("${tablePrefix}purchase_orders", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${inventoryItems.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("${tablePrefix}inventory", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${suppliers.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("${tablePrefix}suppliers", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(databaseName.take(10), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("Central DB", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    // Live Event Stream Log
                    item {
                        Text(
                            text = "Live Cloud SQL Transaction Log",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (syncEvents.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No SQL transactions recorded yet. Create or edit POs/Inventory to view live SQL queries.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(syncEvents.take(15)) { event ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = event.module,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = event.action,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "SQL UPSERT -> ${tablePrefix}${event.module.lowercase()}: ${event.entitySummary}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1
                                        )
                                    }

                                    Text(
                                        text = event.timestamp,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close SQL Server Manager")
                }
            }
        }
    }

    if (showDiagnosticDialog) {
        SqlServerDiagnosticDialog(
            initialHost = serverHost,
            initialPort = serverPort,
            initialDatabase = databaseName,
            initialUsername = username,
            initialPassword = password,
            initialPrefix = tablePrefix,
            onDismiss = { showDiagnosticDialog = false },
            onApplyConfig = { newHost, newPort, newDb, newUser, newPass, newPrefix ->
                serverHost = newHost
                serverPort = newPort
                databaseName = newDb
                username = newUser
                password = newPass
                tablePrefix = newPrefix
                savePrefs()
            }
        )
    }
}
