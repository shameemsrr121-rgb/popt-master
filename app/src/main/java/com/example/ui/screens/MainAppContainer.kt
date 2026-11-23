package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.service.SessionManager
import com.example.ui.components.AdminControlDialog
import com.example.ui.components.AppSettingsDialog
import com.example.ui.components.CloudDatabaseSyncDialog
import com.example.ui.components.PoptLogoIcon
import com.example.ui.components.PurchasePlanningCalendarDialog
import com.example.ui.viewmodel.POMasterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    viewModel: POMasterViewModel
) {
    val context = LocalContext.current
    val savedSession = remember { SessionManager.getSavedSession(context) }
    var userSession by remember { mutableStateOf<UserSession?>(savedSession) }
    var selectedMasterTab by remember { mutableIntStateOf(0) }
    var showAdminControlDialog by remember { mutableStateOf(false) }
    var showCloudDatabaseDialog by remember { mutableStateOf(false) }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var showSupplierDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    val openPOs by viewModel.purchaseOrdersState.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val companyProfile by viewModel.companyProfileState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Sync user rights in background with Room Database if user is logged in
    LaunchedEffect(userSession?.username) {
        val currentUsername = userSession?.username
        if (!currentUsername.isNullOrBlank()) {
            viewModel.getUserByUsername(currentUsername) { dbUser ->
                if (dbUser != null) {
                    SessionManager.updateSessionWithUser(context, dbUser)
                    userSession = UserSession(
                        id = dbUser.id,
                        username = dbUser.username,
                        fullName = dbUser.fullName,
                        role = dbUser.role,
                        isAdmin = dbUser.isAdmin,
                        canDeletePO = dbUser.canDeletePO,
                        showProcurement = dbUser.showProcurement,
                        showInventory = dbUser.showInventory,
                        showSupplier = dbUser.showSupplier,
                        showReport = dbUser.showReport,
                        showMargin = dbUser.showMargin,
                        showGoogleSheets = dbUser.showGoogleSheets,
                        showAdminPanel = dbUser.isAdmin || dbUser.showAdminPanel,
                        isLoggedIn = true
                    )
                }
            }
        }
    }

    val openPOCount = remember(openPOs) { openPOs.count { it.status == "ISSUED" || it.status == "PARTIALLY_RECEIVED" } }

    LaunchedEffect(lastSyncStatus) {
        lastSyncStatus?.let { status ->
            snackbarHostState.showSnackbar(
                message = status,
                duration = SnackbarDuration.Short
            )
            viewModel.clearLastSyncStatus()
        }
    }

    if (userSession == null) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = { session ->
                userSession = session
                selectedMasterTab = 0
            }
        )
        return
    }

    val currentSession = userSession!!
    val canAccessAdmin = currentSession.isAdmin || currentSession.showAdminPanel
    val canAccessCloudDb = currentSession.isAdmin || currentSession.showGoogleSheets

    if (showAdminControlDialog && canAccessAdmin) {
        AdminControlDialog(
            viewModel = viewModel,
            onDismiss = { showAdminControlDialog = false }
        )
    }

    if (showCloudDatabaseDialog && canAccessCloudDb) {
        CloudDatabaseSyncDialog(
            viewModel = viewModel,
            onDismiss = { showCloudDatabaseDialog = false }
        )
    }

    if (showCalendarDialog) {
        PurchasePlanningCalendarDialog(
            viewModel = viewModel,
            onDismiss = { showCalendarDialog = false }
        )
    }

    if (showSupplierDialog) {
        Dialog(
            onDismissRequest = { showSupplierDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Supplier Directory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showSupplierDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    SupplierMasterScreen(viewModel = viewModel)
                }
            }
        }
    }

    if (showSettingsDialog) {
        AppSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
        )
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            properties = DialogProperties(decorFitsSystemWindows = false),
            icon = {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Logout",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of ${currentSession.fullName.ifBlank { currentSession.username }}?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        SessionManager.clearActiveSession(context)
                        userSession = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log Out")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLogoutConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_logout_button")
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 2.dp)
                    ) {
                        PoptLogoIcon(
                            size = 26.dp,
                            showText = true,
                            cornerRadius = 5.dp
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = when (selectedMasterTab) {
                                0 -> companyProfile.companyName.ifBlank { "PO Tracker & Planner" }
                                1 -> "Purchase Orders"
                                2 -> "Order Planner"
                                3 -> "PO Status Tracker"
                                4 -> "PO Reports & Spend"
                                5 -> "Inventory & Tax Structure"
                                else -> companyProfile.companyName.ifBlank { "PO Master" }
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "User: ${currentSession.fullName.ifBlank { currentSession.username }} (${currentSession.role})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    // Quick Access to Inventory & Tax Structure
                    if (currentSession.showInventory) {
                        IconButton(
                            onClick = { selectedMasterTab = 5 },
                            modifier = Modifier.testTag("top_bar_inventory_button")
                        ) {
                            Icon(
                                Icons.Default.Inventory2,
                                contentDescription = "Inventory & Tax Structure",
                                tint = if (selectedMasterTab == 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Supplier Directory Quick Access
                    IconButton(
                        onClick = { showSupplierDialog = true },
                        modifier = Modifier.testTag("top_bar_suppliers_button")
                    ) {
                        Icon(
                            Icons.Default.Business,
                            contentDescription = "Supplier Directory",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Calendar
                    IconButton(
                        onClick = { showCalendarDialog = true },
                        modifier = Modifier.testTag("top_bar_calendar_button")
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "PO Planning Calendar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (canAccessCloudDb) {
                        IconButton(
                            onClick = { showCloudDatabaseDialog = true },
                            modifier = Modifier.testTag("top_bar_cloud_db_button")
                        ) {
                            Icon(
                                Icons.Default.Dns,
                                contentDescription = "Cloud SQL Central Sync",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (canAccessAdmin) {
                        IconButton(
                            onClick = { showAdminControlDialog = true },
                            modifier = Modifier.testTag("top_bar_admin_control_button")
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Control Panel",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier.testTag("top_bar_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = "App Settings & Alerts",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { showLogoutConfirmDialog = true },
                        modifier = Modifier.testTag("top_bar_logout_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Log Out",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("main_bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = selectedMasterTab == 0,
                    onClick = { selectedMasterTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("nav_item_dashboard")
                )

                NavigationBarItem(
                    selected = selectedMasterTab == 1,
                    onClick = { selectedMasterTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (openPOCount > 0) {
                                    Badge { Text("$openPOCount") }
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Orders")
                        }
                    },
                    label = { Text("Orders") },
                    modifier = Modifier.testTag("nav_item_orders")
                )

                NavigationBarItem(
                    selected = selectedMasterTab == 2,
                    onClick = { selectedMasterTab = 2 },
                    icon = { Icon(Icons.Default.EventNote, contentDescription = "Planner") },
                    label = { Text("Planner") },
                    modifier = Modifier.testTag("nav_item_planner")
                )

                NavigationBarItem(
                    selected = selectedMasterTab == 3,
                    onClick = { selectedMasterTab = 3 },
                    icon = { Icon(Icons.Default.Timeline, contentDescription = "Tracker") },
                    label = { Text("Tracker") },
                    modifier = Modifier.testTag("nav_item_tracker")
                )

                NavigationBarItem(
                    selected = selectedMasterTab == 4,
                    onClick = { selectedMasterTab = 4 },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = "Reports") },
                    label = { Text("Reports") },
                    modifier = Modifier.testTag("nav_item_reports")
                )

                if (currentSession.showInventory) {
                    NavigationBarItem(
                        selected = selectedMasterTab == 5,
                        onClick = { selectedMasterTab = 5 },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = "Inventory") },
                        label = { Text("Inventory") },
                        modifier = Modifier.testTag("nav_item_inventory")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedMasterTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "master_tab_transition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToMaster = { masterIndex -> selectedMasterTab = masterIndex },
                        onOpenCreatePO = { selectedMasterTab = 1 },
                        onOpenInventory = { selectedMasterTab = 5 },
                        onOpenAdminPanel = { showAdminControlDialog = true },
                        onOpenSettings = { showSettingsDialog = true },
                        onOpenCloudSync = { showCloudDatabaseDialog = true }
                    )
                    1 -> ProcurementMasterScreen(viewModel = viewModel, userSession = currentSession)
                    2 -> PlannerScreen(
                        viewModel = viewModel,
                        onConvertToPO = {
                            selectedMasterTab = 1
                        }
                    )
                    3 -> TrackerScreen(
                        viewModel = viewModel,
                        onNavigateToCreatePO = { selectedMasterTab = 1 },
                        onViewPODetail = { selectedMasterTab = 1 }
                    )
                    4 -> ReportMasterScreen(viewModel = viewModel)
                    5 -> InventoryMasterScreen(viewModel = viewModel)
                    else -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToMaster = { masterIndex -> selectedMasterTab = masterIndex }
                    )
                }
            }
        }
    }
}
