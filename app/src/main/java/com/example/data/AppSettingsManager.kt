package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class SoundSourceType {
    PRESET,
    SYSTEM_NOTIFICATION,
    SYSTEM_ALARM,
    SYSTEM_RINGTONE,
    CUSTOM_AUDIO
}

data class AppSettings(
    // Push Notifications & Alert Schedule
    val notificationsEnabled: Boolean = true,
    val poDeliveryAlertsEnabled: Boolean = true,
    val poDeliveryAlertDaysBefore: Int = 2, // 0 = Same day, 1 = 1 day before, 2 = 2 days before, etc.
    val poOverdueAlertsEnabled: Boolean = true,
    val purchasePlanAlertsEnabled: Boolean = true,
    val purchasePlanAlertDaysBefore: Int = 1, // 0 = Due date, 1 = 1 day before, etc.
    val lowStockAlertsEnabled: Boolean = true,

    // Sound & Vibration
    val soundAlertsEnabled: Boolean = true,
    val vibrateAlertsEnabled: Boolean = true,
    val soundSourceType: SoundSourceType = SoundSourceType.PRESET,
    val selectedPresetSound: String = "Crystal Chime", // Crystal Chime, Service Bell, Modern Ping, Soft Melody, Elegant Harp, Urgent Alert
    val customAudioUri: String = "",
    val customAudioTitle: String = "",

    // Display & Theme
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColorName: String = "GARDEN_RED", // GARDEN_RED, EMERALD_GREEN, ROYAL_INDIGO, SAPPHIRE_BLUE, AMBER_GOLD

    // Periodic SQL Sync & Data Integrity Verification Reminders
    val sqlSyncReminderEnabled: Boolean = true,
    val sqlSyncReminderIntervalHours: Int = 24, // 1, 4, 12, 24, 48, 168 (7 days)
    val lastSqlSyncTimestampMillis: Long = 0L,
    val autoExportIntegrityLog: Boolean = true,
    val lastIntegritySummary: String = ""
)

class AppSettingsManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settingsState = MutableStateFlow(loadSettings())
    val settingsState: StateFlow<AppSettings> = _settingsState.asStateFlow()

    private fun loadSettings(): AppSettings {
        val themeModeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }

        val soundTypeStr = prefs.getString(KEY_SOUND_SOURCE_TYPE, SoundSourceType.PRESET.name) ?: SoundSourceType.PRESET.name
        val soundSourceType = try {
            SoundSourceType.valueOf(soundTypeStr)
        } catch (_: Exception) {
            SoundSourceType.PRESET
        }

        return AppSettings(
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            poDeliveryAlertsEnabled = prefs.getBoolean(KEY_PO_DELIVERY_ALERTS_ENABLED, true),
            poDeliveryAlertDaysBefore = prefs.getInt(KEY_PO_DELIVERY_ALERT_DAYS, 2),
            poOverdueAlertsEnabled = prefs.getBoolean(KEY_PO_OVERDUE_ALERTS_ENABLED, true),
            purchasePlanAlertsEnabled = prefs.getBoolean(KEY_PURCHASE_PLAN_ALERTS_ENABLED, true),
            purchasePlanAlertDaysBefore = prefs.getInt(KEY_PURCHASE_PLAN_ALERT_DAYS, 1),
            lowStockAlertsEnabled = prefs.getBoolean(KEY_LOW_STOCK_ALERTS_ENABLED, true),

            soundAlertsEnabled = prefs.getBoolean(KEY_SOUND_ALERTS_ENABLED, true),
            vibrateAlertsEnabled = prefs.getBoolean(KEY_VIBRATE_ALERTS_ENABLED, true),
            soundSourceType = soundSourceType,
            selectedPresetSound = prefs.getString(KEY_SELECTED_PRESET_SOUND, "Crystal Chime") ?: "Crystal Chime",
            customAudioUri = prefs.getString(KEY_CUSTOM_AUDIO_URI, "") ?: "",
            customAudioTitle = prefs.getString(KEY_CUSTOM_AUDIO_TITLE, "") ?: "",

            themeMode = themeMode,
            accentColorName = prefs.getString(KEY_ACCENT_COLOR_NAME, "GARDEN_RED") ?: "GARDEN_RED",

            sqlSyncReminderEnabled = prefs.getBoolean(KEY_SQL_SYNC_REMINDER_ENABLED, true),
            sqlSyncReminderIntervalHours = prefs.getInt(KEY_SQL_SYNC_REMINDER_INTERVAL_HOURS, 24),
            lastSqlSyncTimestampMillis = prefs.getLong(KEY_LAST_SQL_SYNC_TIMESTAMP_MILLIS, 0L),
            autoExportIntegrityLog = prefs.getBoolean(KEY_AUTO_EXPORT_INTEGRITY_LOG, true),
            lastIntegritySummary = prefs.getString(KEY_LAST_INTEGRITY_SUMMARY, "") ?: ""
        )
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, newSettings.notificationsEnabled)
            .putBoolean(KEY_PO_DELIVERY_ALERTS_ENABLED, newSettings.poDeliveryAlertsEnabled)
            .putInt(KEY_PO_DELIVERY_ALERT_DAYS, newSettings.poDeliveryAlertDaysBefore)
            .putBoolean(KEY_PO_OVERDUE_ALERTS_ENABLED, newSettings.poOverdueAlertsEnabled)
            .putBoolean(KEY_PURCHASE_PLAN_ALERTS_ENABLED, newSettings.purchasePlanAlertsEnabled)
            .putInt(KEY_PURCHASE_PLAN_ALERT_DAYS, newSettings.purchasePlanAlertDaysBefore)
            .putBoolean(KEY_LOW_STOCK_ALERTS_ENABLED, newSettings.lowStockAlertsEnabled)

            .putBoolean(KEY_SOUND_ALERTS_ENABLED, newSettings.soundAlertsEnabled)
            .putBoolean(KEY_VIBRATE_ALERTS_ENABLED, newSettings.vibrateAlertsEnabled)
            .putString(KEY_SOUND_SOURCE_TYPE, newSettings.soundSourceType.name)
            .putString(KEY_SELECTED_PRESET_SOUND, newSettings.selectedPresetSound)
            .putString(KEY_CUSTOM_AUDIO_URI, newSettings.customAudioUri)
            .putString(KEY_CUSTOM_AUDIO_TITLE, newSettings.customAudioTitle)

            .putString(KEY_THEME_MODE, newSettings.themeMode.name)
            .putString(KEY_ACCENT_COLOR_NAME, newSettings.accentColorName)

            .putBoolean(KEY_SQL_SYNC_REMINDER_ENABLED, newSettings.sqlSyncReminderEnabled)
            .putInt(KEY_SQL_SYNC_REMINDER_INTERVAL_HOURS, newSettings.sqlSyncReminderIntervalHours)
            .putLong(KEY_LAST_SQL_SYNC_TIMESTAMP_MILLIS, newSettings.lastSqlSyncTimestampMillis)
            .putBoolean(KEY_AUTO_EXPORT_INTEGRITY_LOG, newSettings.autoExportIntegrityLog)
            .putString(KEY_LAST_INTEGRITY_SUMMARY, newSettings.lastIntegritySummary)
            .apply()

        _settingsState.value = newSettings
    }

    fun setThemeMode(mode: ThemeMode) {
        updateSettings(_settingsState.value.copy(themeMode = mode))
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        updateSettings(_settingsState.value.copy(notificationsEnabled = enabled))
    }

    fun setPODeliveryAlerts(enabled: Boolean, daysBefore: Int) {
        updateSettings(
            _settingsState.value.copy(
                poDeliveryAlertsEnabled = enabled,
                poDeliveryAlertDaysBefore = daysBefore
            )
        )
    }

    fun setPurchasePlanAlerts(enabled: Boolean, daysBefore: Int) {
        updateSettings(
            _settingsState.value.copy(
                purchasePlanAlertsEnabled = enabled,
                purchasePlanAlertDaysBefore = daysBefore
            )
        )
    }

    fun setSqlSyncReminder(enabled: Boolean, intervalHours: Int, autoExport: Boolean) {
        updateSettings(
            _settingsState.value.copy(
                sqlSyncReminderEnabled = enabled,
                sqlSyncReminderIntervalHours = intervalHours,
                autoExportIntegrityLog = autoExport
            )
        )
    }

    fun updateLastSqlSyncTimestamp(timestampMillis: Long, summary: String = "") {
        updateSettings(
            _settingsState.value.copy(
                lastSqlSyncTimestampMillis = timestampMillis,
                lastIntegritySummary = summary.ifBlank { _settingsState.value.lastIntegritySummary }
            )
        )
    }

    fun setSoundCustomization(
        soundEnabled: Boolean,
        vibrateEnabled: Boolean,
        sourceType: SoundSourceType,
        presetName: String,
        customUri: String = "",
        customTitle: String = ""
    ) {
        updateSettings(
            _settingsState.value.copy(
                soundAlertsEnabled = soundEnabled,
                vibrateAlertsEnabled = vibrateEnabled,
                soundSourceType = sourceType,
                selectedPresetSound = presetName,
                customAudioUri = customUri,
                customAudioTitle = customTitle
            )
        )
    }

    companion object {
        private const val PREFS_NAME = "garden_po_master_settings_prefs"

        private const val KEY_NOTIFICATIONS_ENABLED = "key_notifications_enabled"
        private const val KEY_PO_DELIVERY_ALERTS_ENABLED = "key_po_delivery_alerts_enabled"
        private const val KEY_PO_DELIVERY_ALERT_DAYS = "key_po_delivery_alert_days"
        private const val KEY_PO_OVERDUE_ALERTS_ENABLED = "key_po_overdue_alerts_enabled"
        private const val KEY_PURCHASE_PLAN_ALERTS_ENABLED = "key_purchase_plan_alerts_enabled"
        private const val KEY_PURCHASE_PLAN_ALERT_DAYS = "key_purchase_plan_alert_days"
        private const val KEY_LOW_STOCK_ALERTS_ENABLED = "key_low_stock_alerts_enabled"

        private const val KEY_SOUND_ALERTS_ENABLED = "key_sound_alerts_enabled"
        private const val KEY_VIBRATE_ALERTS_ENABLED = "key_vibrate_alerts_enabled"
        private const val KEY_SOUND_SOURCE_TYPE = "key_sound_source_type"
        private const val KEY_SELECTED_PRESET_SOUND = "key_selected_preset_sound"
        private const val KEY_CUSTOM_AUDIO_URI = "key_custom_audio_uri"
        private const val KEY_CUSTOM_AUDIO_TITLE = "key_custom_audio_title"

        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_ACCENT_COLOR_NAME = "key_accent_color_name"

        private const val KEY_SQL_SYNC_REMINDER_ENABLED = "key_sql_sync_reminder_enabled"
        private const val KEY_SQL_SYNC_REMINDER_INTERVAL_HOURS = "key_sql_sync_reminder_interval_hours"
        private const val KEY_LAST_SQL_SYNC_TIMESTAMP_MILLIS = "key_last_sql_sync_timestamp_millis"
        private const val KEY_AUTO_EXPORT_INTEGRITY_LOG = "key_auto_export_integrity_log"
        private const val KEY_LAST_INTEGRITY_SUMMARY = "key_last_integrity_summary"

        @Volatile
        private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
