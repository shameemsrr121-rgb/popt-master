package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.AppSettings
import com.example.data.SoundSourceType
import com.example.data.ThemeMode
import com.example.ui.theme.*
import com.example.ui.viewmodel.POMasterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    viewModel: POMasterViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentSettings by viewModel.appSettingsState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    // Temporary working state for live editing before or on apply
    var notificationsEnabled by remember(currentSettings) { mutableStateOf(currentSettings.notificationsEnabled) }
    var poAlertsEnabled by remember(currentSettings) { mutableStateOf(currentSettings.poDeliveryAlertsEnabled) }
    var poAlertDays by remember(currentSettings) { mutableIntStateOf(currentSettings.poDeliveryAlertDaysBefore) }
    var poOverdueAlerts by remember(currentSettings) { mutableStateOf(currentSettings.poOverdueAlertsEnabled) }
    var planAlertsEnabled by remember(currentSettings) { mutableStateOf(currentSettings.purchasePlanAlertsEnabled) }
    var planAlertDays by remember(currentSettings) { mutableIntStateOf(currentSettings.purchasePlanAlertDaysBefore) }
    var lowStockAlerts by remember(currentSettings) { mutableStateOf(currentSettings.lowStockAlertsEnabled) }

    var soundEnabled by remember(currentSettings) { mutableStateOf(currentSettings.soundAlertsEnabled) }
    var vibrateEnabled by remember(currentSettings) { mutableStateOf(currentSettings.vibrateAlertsEnabled) }
    var soundType by remember(currentSettings) { mutableStateOf(currentSettings.soundSourceType) }
    var selectedPreset by remember(currentSettings) { mutableStateOf(currentSettings.selectedPresetSound) }
    var customAudioUri by remember(currentSettings) { mutableStateOf(currentSettings.customAudioUri) }
    var customAudioTitle by remember(currentSettings) { mutableStateOf(currentSettings.customAudioTitle) }

    var themeMode by remember(currentSettings) { mutableStateOf(currentSettings.themeMode) }
    var accentColorName by remember(currentSettings) { mutableStateOf(currentSettings.accentColorName) }

    // SQL Sync Reminder & Data Integrity State
    var sqlSyncReminderEnabled by remember(currentSettings) { mutableStateOf(currentSettings.sqlSyncReminderEnabled) }
    var sqlSyncReminderIntervalHours by remember(currentSettings) { mutableIntStateOf(currentSettings.sqlSyncReminderIntervalHours) }
    var autoExportIntegrityLog by remember(currentSettings) { mutableStateOf(currentSettings.autoExportIntegrityLog) }

    var showReportPreviewDialog by remember { mutableStateOf(false) }
    var showDiagnosticDialog by remember { mutableStateOf(false) }
    var previewReportText by remember { mutableStateOf("") }
    var syncFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isSyncingNow by remember { mutableStateOf(false) }

    // Notification permission launcher for Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            notificationsEnabled = true
        }
    }

    // Audio file picker from gallery / music / files
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            customAudioUri = uri.toString()
            customAudioTitle = queryFileName(context, uri) ?: "Selected Audio Track"
            soundType = SoundSourceType.CUSTOM_AUDIO
            // Update settings immediately so preview works
            viewModel.setSoundCustomization(
                soundEnabled = soundEnabled,
                vibrateEnabled = vibrateEnabled,
                sourceType = SoundSourceType.CUSTOM_AUDIO,
                presetName = selectedPreset,
                customUri = customAudioUri,
                customTitle = customAudioTitle
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopPreviewSound()
        }
    }

    fun saveAllSettings() {
        val updated = AppSettings(
            notificationsEnabled = notificationsEnabled,
            poDeliveryAlertsEnabled = poAlertsEnabled,
            poDeliveryAlertDaysBefore = poAlertDays,
            poOverdueAlertsEnabled = poOverdueAlerts,
            purchasePlanAlertsEnabled = planAlertsEnabled,
            purchasePlanAlertDaysBefore = planAlertDays,
            lowStockAlertsEnabled = lowStockAlerts,
            soundAlertsEnabled = soundEnabled,
            vibrateAlertsEnabled = vibrateEnabled,
            soundSourceType = soundType,
            selectedPresetSound = selectedPreset,
            customAudioUri = customAudioUri,
            customAudioTitle = customAudioTitle,
            themeMode = themeMode,
            accentColorName = accentColorName,
            sqlSyncReminderEnabled = sqlSyncReminderEnabled,
            sqlSyncReminderIntervalHours = sqlSyncReminderIntervalHours,
            autoExportIntegrityLog = autoExportIntegrityLog
        )
        viewModel.updateAppSettings(updated)
    }

    if (showReportPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showReportPreviewDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("SQL Data Integrity Audit", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        item {
                            Text(
                                text = previewReportText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.shareDataIntegrityLog(context)
                        showReportPreviewDialog = false
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Export Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReportPreviewDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            saveAllSettings()
            onDismiss()
        },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.90f),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "App Settings & Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Alerts, Sound & Display Configuration",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = {
                        saveAllSettings()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("close_settings_dialog_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Settings")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Tab Navigation
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Alerts & Push", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("settings_tab_notifications")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("SQL Integrity & Sync", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.SyncLock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("settings_tab_sql_sync")
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Display & Theme", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("settings_tab_display")
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Sound & Music", style = MaterialTheme.typography.labelMedium) },
                        icon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("settings_tab_sound")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "settings_tab_anim"
                ) { tab ->
                    when (tab) {
                        0 -> NotificationsSettingsTab(
                            notificationsEnabled = notificationsEnabled,
                            onNotificationsEnabledChange = {
                                notificationsEnabled = it
                                viewModel.setNotificationsEnabled(it)
                            },
                            hasNotificationPermission = hasNotificationPermission,
                            onRequestPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            poAlertsEnabled = poAlertsEnabled,
                            onPoAlertsEnabledChange = {
                                poAlertsEnabled = it
                                viewModel.setPODeliveryAlerts(it, poAlertDays)
                            },
                            poAlertDays = poAlertDays,
                            onPoAlertDaysChange = {
                                poAlertDays = it
                                viewModel.setPODeliveryAlerts(poAlertsEnabled, it)
                            },
                            poOverdueAlerts = poOverdueAlerts,
                            onPoOverdueAlertsChange = { poOverdueAlerts = it },
                            planAlertsEnabled = planAlertsEnabled,
                            onPlanAlertsEnabledChange = {
                                planAlertsEnabled = it
                                viewModel.setPurchasePlanAlerts(it, planAlertDays)
                            },
                            planAlertDays = planAlertDays,
                            onPlanAlertDaysChange = {
                                planAlertDays = it
                                viewModel.setPurchasePlanAlerts(planAlertsEnabled, it)
                            },
                            lowStockAlerts = lowStockAlerts,
                            onLowStockAlertsChange = { lowStockAlerts = it },
                            onTriggerTestAlert = { viewModel.triggerTestAlert() },
                            viewModel = viewModel
                        )
                        1 -> SqlSyncIntegritySettingsTab(
                            sqlSyncReminderEnabled = sqlSyncReminderEnabled,
                            onSqlSyncReminderEnabledChange = {
                                sqlSyncReminderEnabled = it
                                viewModel.setSqlSyncReminder(it, sqlSyncReminderIntervalHours, autoExportIntegrityLog)
                            },
                            sqlSyncReminderIntervalHours = sqlSyncReminderIntervalHours,
                            onIntervalHoursChange = {
                                sqlSyncReminderIntervalHours = it
                                viewModel.setSqlSyncReminder(sqlSyncReminderEnabled, it, autoExportIntegrityLog)
                            },
                            autoExportIntegrityLog = autoExportIntegrityLog,
                            onAutoExportChange = {
                                autoExportIntegrityLog = it
                                viewModel.setSqlSyncReminder(sqlSyncReminderEnabled, sqlSyncReminderIntervalHours, it)
                            },
                            onGenerateReport = {
                                val report = viewModel.generateDataIntegrityReport()
                                previewReportText = report
                                showReportPreviewDialog = true
                            },
                            onExportShareLog = {
                                viewModel.shareDataIntegrityLog(context)
                            },
                            onTriggerSyncNow = {
                                isSyncingNow = true
                                syncFeedbackMessage = "Performing full SQL Server synchronization..."
                                viewModel.syncFullDatabaseToCloud { success, msg ->
                                    isSyncingNow = false
                                    syncFeedbackMessage = if (success) "✓ Sync Completed: $msg" else "✕ Sync Error: $msg"
                                }
                            },
                            onOpenDiagnostics = {
                                showDiagnosticDialog = true
                            },
                            isSyncingNow = isSyncingNow,
                            syncFeedbackMessage = syncFeedbackMessage,
                            viewModel = viewModel
                        )
                        2 -> DisplayThemeSettingsTab(
                            themeMode = themeMode,
                            onThemeModeChange = {
                                themeMode = it
                                viewModel.setThemeMode(it)
                            },
                            accentColorName = accentColorName,
                            onAccentColorChange = {
                                accentColorName = it
                                viewModel.updateAppSettings(
                                    currentSettings.copy(accentColorName = it)
                                )
                            }
                        )
                        3 -> SoundSettingsTab(
                            soundEnabled = soundEnabled,
                            onSoundEnabledChange = {
                                soundEnabled = it
                                viewModel.setSoundCustomization(
                                    it, vibrateEnabled, soundType, selectedPreset, customAudioUri, customAudioTitle
                                )
                            },
                            vibrateEnabled = vibrateEnabled,
                            onVibrateEnabledChange = {
                                vibrateEnabled = it
                                viewModel.setSoundCustomization(
                                    soundEnabled, it, soundType, selectedPreset, customAudioUri, customAudioTitle
                                )
                            },
                            soundType = soundType,
                            onSoundTypeChange = {
                                soundType = it
                                viewModel.setSoundCustomization(
                                    soundEnabled, vibrateEnabled, it, selectedPreset, customAudioUri, customAudioTitle
                                )
                            },
                            selectedPreset = selectedPreset,
                            onPresetChange = {
                                selectedPreset = it
                                soundType = SoundSourceType.PRESET
                                viewModel.setSoundCustomization(
                                    soundEnabled, vibrateEnabled, SoundSourceType.PRESET, it, customAudioUri, customAudioTitle
                                )
                                viewModel.playPreviewSound(
                                    currentSettings.copy(
                                        soundAlertsEnabled = true,
                                        soundSourceType = SoundSourceType.PRESET,
                                        selectedPresetSound = it
                                    )
                                )
                            },
                            customAudioUri = customAudioUri,
                            customAudioTitle = customAudioTitle,
                            onPickAudio = {
                                audioPickerLauncher.launch("audio/*")
                            },
                            onClearCustomAudio = {
                                customAudioUri = ""
                                customAudioTitle = ""
                                soundType = SoundSourceType.PRESET
                                viewModel.setSoundCustomization(
                                    soundEnabled, vibrateEnabled, SoundSourceType.PRESET, selectedPreset, "", ""
                                )
                            },
                            onPlayPreview = {
                                val previewSettings = AppSettings(
                                    soundAlertsEnabled = true,
                                    vibrateAlertsEnabled = vibrateEnabled,
                                    soundSourceType = soundType,
                                    selectedPresetSound = selectedPreset,
                                    customAudioUri = customAudioUri,
                                    customAudioTitle = customAudioTitle
                                )
                                isPreviewPlaying = true
                                viewModel.playPreviewSound(previewSettings)
                            },
                            onStopPreview = {
                                isPreviewPlaying = false
                                viewModel.stopPreviewSound()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    saveAllSettings()
                    onDismiss()
                },
                modifier = Modifier.testTag("save_and_close_settings_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Done")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    saveAllSettings()
                    onDismiss()
                }
            ) {
                Text("Dismiss")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )

    if (showDiagnosticDialog) {
        SqlServerDiagnosticDialog(
            onDismiss = { showDiagnosticDialog = false }
        )
    }
}

@Composable
private fun NotificationsSettingsTab(
    notificationsEnabled: Boolean,
    onNotificationsEnabledChange: (Boolean) -> Unit,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit,
    poAlertsEnabled: Boolean,
    onPoAlertsEnabledChange: (Boolean) -> Unit,
    poAlertDays: Int,
    onPoAlertDaysChange: (Int) -> Unit,
    poOverdueAlerts: Boolean,
    onPoOverdueAlertsChange: (Boolean) -> Unit,
    planAlertsEnabled: Boolean,
    onPlanAlertsEnabledChange: (Boolean) -> Unit,
    planAlertDays: Int,
    onPlanAlertDaysChange: (Int) -> Unit,
    lowStockAlerts: Boolean,
    onLowStockAlertsChange: (Boolean) -> Unit,
    onTriggerTestAlert: () -> Unit,
    viewModel: POMasterViewModel
) {
    val approachingPOs = remember(poAlertsEnabled, poAlertDays, poOverdueAlerts) {
        viewModel.getApproachingPODeliveries()
    }
    val approachingPlans = remember(planAlertsEnabled, planAlertDays) {
        viewModel.getApproachingPurchasePlans()
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Master Push Notifications Switch
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (notificationsEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (notificationsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = if (notificationsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Push & In-App Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (notificationsEnabled) "Active on this device" else "Alerts currently paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsEnabledChange,
                        modifier = Modifier.testTag("toggle_master_notifications")
                    )
                }
            }
        }

        // Permission Banner if not granted on Android 13+
        if (!hasNotificationPermission) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "System Notification Permission Needed",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "Allow PO Master to send lock-screen & status bar push alerts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Section 1: PO Delivery Date Alerts Customization
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Approaching PO Delivery Alerts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = poAlertsEnabled,
                            onCheckedChange = onPoAlertsEnabledChange,
                            enabled = notificationsEnabled,
                            modifier = Modifier.testTag("toggle_po_delivery_alerts")
                        )
                    }

                    Text(
                        "Notify in advance before purchase order expected delivery date arrives:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Days in advance chips
                    val poDayOptions = listOf(
                        0 to "Same Day",
                        1 to "1 Day Before",
                        2 to "2 Days Before",
                        3 to "3 Days Before",
                        5 to "5 Days Before",
                        7 to "1 Week Before"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        poDayOptions.take(3).forEach { (days, label) ->
                            FilterChip(
                                selected = poAlertDays == days,
                                onClick = { onPoAlertDaysChange(days) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                enabled = notificationsEnabled && poAlertsEnabled,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        poDayOptions.drop(3).forEach { (days, label) ->
                            FilterChip(
                                selected = poAlertDays == days,
                                onClick = { onPoAlertDaysChange(days) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                enabled = notificationsEnabled && poAlertsEnabled,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Overdue PO Warning Alerts",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Receive alert if delivery date has passed without full receipt",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = poOverdueAlerts,
                            onCheckedChange = onPoOverdueAlertsChange,
                            enabled = notificationsEnabled && poAlertsEnabled,
                            modifier = Modifier.testTag("toggle_po_overdue_alerts")
                        )
                    }

                    // Live active matches badge
                    if (approachingPOs.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${approachingPOs.size} PO(s) currently match your alert threshold (${if (poAlertDays == 0) "Today" else "next $poAlertDays days"}).",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Purchase Plan Alerts Customization
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Approaching Purchase Plan Alerts",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Switch(
                            checked = planAlertsEnabled,
                            onCheckedChange = onPlanAlertsEnabledChange,
                            enabled = notificationsEnabled,
                            modifier = Modifier.testTag("toggle_plan_alerts")
                        )
                    }

                    Text(
                        "Notify in advance before planned supplier buying/trip dates:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Plan day options chips
                    val planDayOptions = listOf(
                        0 to "On Date",
                        1 to "1 Day Before",
                        2 to "2 Days Before",
                        3 to "3 Days Before",
                        5 to "5 Days Before"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        planDayOptions.forEach { (days, label) ->
                            FilterChip(
                                selected = planAlertDays == days,
                                onClick = { onPlanAlertDaysChange(days) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                enabled = notificationsEnabled && planAlertsEnabled,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (approachingPlans.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Event,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${approachingPlans.size} Purchase Plan(s) scheduled within next $planAlertDays days.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Low Stock Alerts & Test Button
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Low Inventory Reorder Alerts",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Alert when stock level dips below reorder minimum",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = lowStockAlerts,
                            onCheckedChange = onLowStockAlertsChange,
                            enabled = notificationsEnabled,
                            modifier = Modifier.testTag("toggle_low_stock_alerts")
                        )
                    }

                    HorizontalDivider()

                    Button(
                        onClick = onTriggerTestAlert,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_test_notification_button")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Test Push Notification & Sound", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayThemeSettingsTab(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    accentColorName: String,
    onAccentColorChange: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                "Theme Mode Appearance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Choose how PO Master looks on your screen. Supports dark and light themes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Theme Mode 3-Card Selector
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Light Mode Card
                ThemeModeCard(
                    title = "Light",
                    subtitle = "Fresh & Crisp",
                    icon = Icons.Default.LightMode,
                    isSelected = themeMode == ThemeMode.LIGHT,
                    previewBackground = Color(0xFFFCFCFF),
                    previewText = Color(0xFF1A1C1E),
                    onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )

                // Dark Mode Card
                ThemeModeCard(
                    title = "Dark",
                    subtitle = "OLED Contrast",
                    icon = Icons.Default.DarkMode,
                    isSelected = themeMode == ThemeMode.DARK,
                    previewBackground = Color(0xFF121417),
                    previewText = Color(0xFFE2E2E6),
                    onClick = { onThemeModeChange(ThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )

                // System Default Card
                ThemeModeCard(
                    title = "System",
                    subtitle = "Auto Device",
                    icon = Icons.Default.SettingsBrightness,
                    isSelected = themeMode == ThemeMode.SYSTEM,
                    previewBackground = Color(0xFF8E9099),
                    previewText = Color.White,
                    onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Brand Accent Palette Selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Brand Accent Color",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Personalize the highlights, buttons, and badges across the master panels:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val accents = listOf(
                        Triple("GARDEN_RED", "Garden Red (Signature)", AccentRed),
                        Triple("EMERALD_GREEN", "Emerald Green", AccentGreen),
                        Triple("ROYAL_INDIGO", "Royal Indigo", AccentIndigo),
                        Triple("SAPPHIRE_BLUE", "Sapphire Blue", AccentBlue),
                        Triple("AMBER_GOLD", "Amber Gold", AccentAmber)
                    )

                    accents.forEach { (key, label, color) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAccentColorChange(key) }
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                        ) {
                            RadioButton(
                                selected = accentColorName == key,
                                onClick = { onAccentColorChange(key) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (accentColorName == key) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    previewBackground: Color,
    previewText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(previewBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = previewText,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(4.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SoundSettingsTab(
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    vibrateEnabled: Boolean,
    onVibrateEnabledChange: (Boolean) -> Unit,
    soundType: SoundSourceType,
    onSoundTypeChange: (SoundSourceType) -> Unit,
    selectedPreset: String,
    onPresetChange: (String) -> Unit,
    customAudioUri: String,
    customAudioTitle: String,
    onPickAudio: () -> Unit,
    onClearCustomAudio: () -> Unit,
    onPlayPreview: () -> Unit,
    onStopPreview: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Sound & Vibrate Switches
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Play Sound on Alerts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Audible chime on upcoming PO dates", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = onSoundEnabledChange,
                            modifier = Modifier.testTag("toggle_sound_alerts")
                        )
                    }

                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Vibration,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Vibrate on Alerts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Haptic feedback pattern", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = onVibrateEnabledChange,
                            modifier = Modifier.testTag("toggle_vibrate_alerts")
                        )
                    }
                }
            }
        }

        // Sound Selection Options
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Alert Audio Source",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // Presets
                    val presets = listOf(
                        "Crystal Chime",
                        "Service Bell",
                        "Modern Ping",
                        "Soft Melody",
                        "Elegant Harp",
                        "Urgent Alert"
                    )

                    Text("Preset Synthesizer Chimes:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        presets.forEach { preset ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = soundEnabled) { onPresetChange(preset) }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            ) {
                                RadioButton(
                                    selected = soundType == SoundSourceType.PRESET && selectedPreset == preset,
                                    onClick = { onPresetChange(preset) },
                                    enabled = soundEnabled
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(preset, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    HorizontalDivider()

                    // System Sounds
                    Text("Device System Sounds:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    val systemOptions = listOf(
                        SoundSourceType.SYSTEM_NOTIFICATION to "Default System Notification Tone",
                        SoundSourceType.SYSTEM_ALARM to "Default System Alarm Tone",
                        SoundSourceType.SYSTEM_RINGTONE to "Default Phone Ringtone"
                    )

                    systemOptions.forEach { (type, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = soundEnabled) { onSoundTypeChange(type) }
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            RadioButton(
                                selected = soundType == type,
                                onClick = { onSoundTypeChange(type) },
                                enabled = soundEnabled
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider()

                    // Custom Audio & Music Picker Option
                    Text("Custom Music & Audio File (Gallery / Storage):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                    if (customAudioUri.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.AudioFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = customAudioTitle.ifBlank { "Custom Audio Selected" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Active Custom Audio Alert",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(onClick = onClearCustomAudio) {
                                    Icon(Icons.Default.Delete, contentDescription = "Clear custom audio", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onPickAudio,
                            enabled = soundEnabled,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (customAudioUri.isNotBlank()) "Change Music File" else "Choose Music / Audio File")
                        }
                    }
                }
            }
        }

        // Live Audio Player Preview Action
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onPlayPreview,
                    enabled = soundEnabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play & Test Sound")
                }

                OutlinedButton(
                    onClick = onStopPreview,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop Sound")
                }
            }
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                cursor.getString(nameIndex)
            } else null
        }
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}

@Composable
private fun SqlSyncIntegritySettingsTab(
    sqlSyncReminderEnabled: Boolean,
    onSqlSyncReminderEnabledChange: (Boolean) -> Unit,
    sqlSyncReminderIntervalHours: Int,
    onIntervalHoursChange: (Int) -> Unit,
    autoExportIntegrityLog: Boolean,
    onAutoExportChange: (Boolean) -> Unit,
    onGenerateReport: () -> Unit,
    onExportShareLog: () -> Unit,
    onTriggerSyncNow: () -> Unit,
    onOpenDiagnostics: () -> Unit = {},
    isSyncingNow: Boolean,
    syncFeedbackMessage: String?,
    viewModel: POMasterViewModel
) {
    val currentSettings by viewModel.appSettingsState.collectAsState()
    val isReminderDue = remember(currentSettings) { viewModel.isSqlSyncReminderDue() }
    val lastSyncMillis = currentSettings.lastSqlSyncTimestampMillis

    val intervals = listOf(
        1 to "Every 1 Hour",
        4 to "Every 4 Hours",
        12 to "Every 12 Hours",
        24 to "Every 24 Hours (Daily)",
        48 to "Every 48 Hours (2 Days)",
        168 to "Every 7 Days (Weekly)"
    )

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Master SQL Reminder Switch Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (sqlSyncReminderEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (sqlSyncReminderEnabled) Icons.Default.SyncLock else Icons.Default.SyncDisabled,
                            contentDescription = null,
                            tint = if (sqlSyncReminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Periodic Data Integrity Reminders",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (sqlSyncReminderEnabled) "Reminders & integrity verification audits active"
                                else "Data integrity reminder alerts paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = sqlSyncReminderEnabled,
                        onCheckedChange = onSqlSyncReminderEnabledChange,
                        modifier = Modifier.testTag("sql_sync_reminder_switch")
                    )
                }
            }
        }

        // Reminder Status & Telemetry Banner
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isReminderDue && sqlSyncReminderEnabled) Color(0xFFFEF2F2)
                    else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (isReminderDue && sqlSyncReminderEnabled) Color(0xFFFECACA)
                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                imageVector = if (isReminderDue && sqlSyncReminderEnabled) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isReminderDue && sqlSyncReminderEnabled) Color(0xFFDC2626) else Color(0xFF16A34A),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isReminderDue && sqlSyncReminderEnabled) "Data Audit Due" else "Data Integrity Up to Date",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isReminderDue && sqlSyncReminderEnabled) Color(0xFFB91C1C) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isReminderDue && sqlSyncReminderEnabled) Color(0xFFFEE2E2) else Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = if (isReminderDue && sqlSyncReminderEnabled) "Audit Needed" else "Verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isReminderDue && sqlSyncReminderEnabled) Color(0xFFDC2626) else Color(0xFF16A34A),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val lastSyncText = if (lastSyncMillis > 0L) {
                        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSyncMillis))
                    } else "No audit logged yet"

                    Text(
                        text = "Last Verified: $lastSyncText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (currentSettings.lastIntegritySummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Status: ${currentSettings.lastIntegritySummary}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Interval Frequency Selection
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Reminder & Audit Frequency",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Specify how frequently you wish to be prompted for a SQL server data integrity audit & sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        intervals.forEach { (hours, label) ->
                            val isSelected = sqlSyncReminderIntervalHours == hours
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = sqlSyncReminderEnabled) { onIntervalHoursChange(hours) }
                                    .testTag("interval_option_$hours")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onIntervalHoursChange(hours) },
                                        enabled = sqlSyncReminderEnabled
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Auto Export Integrity Log Option
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Generate Verification Log",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Compile detailed record checksums and validation digests automatically",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoExportIntegrityLog,
                        onCheckedChange = onAutoExportChange,
                        enabled = sqlSyncReminderEnabled,
                        modifier = Modifier.testTag("auto_export_log_switch")
                    )
                }
            }
        }

        // Trigger / Export Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onExportShareLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("export_share_integrity_log_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export & Share Integrity Log")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onGenerateReport,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("view_integrity_report_btn")
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("View Audit Details")
                    }

                    FilledTonalButton(
                        onClick = onTriggerSyncNow,
                        enabled = !isSyncingNow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("trigger_sync_now_btn")
                    ) {
                        if (isSyncingNow) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isSyncingNow) "Syncing..." else "Sync Cloud Now")
                    }
                }

                // Deep SQL Server Diagnostic & Schema Troubleshooter
                FilledTonalButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_sql_diagnostic_btn"),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Troubleshoot, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🔬 Live SQL Server Diagnostics & Repair", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                if (syncFeedbackMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = syncFeedbackMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
