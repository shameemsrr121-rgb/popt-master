package com.example.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.entity.UserAccountEntity
import com.example.ui.screens.UserSession

object SessionManager {
    private const val TAG = "SessionManager"
    private const val PREFS_NAME = "po_master_auth_session_prefs"

    private const val KEY_IS_LOGGED_IN = "is_logged_in"
    private const val KEY_REMEMBER_ME = "remember_me"
    private const val KEY_REMEMBERED_USERNAME = "remembered_username"
    private const val KEY_REMEMBERED_PASSWORD = "remembered_password"

    // User details cached in session
    private const val KEY_USER_ID = "session_user_id"
    private const val KEY_USERNAME = "session_username"
    private const val KEY_FULL_NAME = "session_full_name"
    private const val KEY_ROLE = "session_role"
    private const val KEY_IS_ADMIN = "session_is_admin"
    private const val KEY_CAN_DELETE_PO = "session_can_delete_po"
    private const val KEY_SHOW_PROCUREMENT = "session_show_procurement"
    private const val KEY_SHOW_INVENTORY = "session_show_inventory"
    private const val KEY_SHOW_SUPPLIER = "session_show_supplier"
    private const val KEY_SHOW_REPORT = "session_show_report"
    private const val KEY_SHOW_MARGIN = "session_show_margin"
    private const val KEY_SHOW_SHEETS = "session_show_sheets"
    private const val KEY_SHOW_ADMIN_PANEL = "session_show_admin_panel"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves active login session and remember me preferences.
     */
    fun saveSession(
        context: Context,
        user: UserAccountEntity,
        rememberMe: Boolean,
        rawPassword: String = ""
    ) {
        val prefs = getPrefs(context)
        Log.i(TAG, "Saving active session for user: '${user.username}' (RememberMe=$rememberMe)")

        val editor = prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putBoolean(KEY_REMEMBER_ME, rememberMe)
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_FULL_NAME, user.fullName)
            .putString(KEY_ROLE, user.role)
            .putBoolean(KEY_IS_ADMIN, user.isAdmin)
            .putBoolean(KEY_CAN_DELETE_PO, user.canDeletePO)
            .putBoolean(KEY_SHOW_PROCUREMENT, user.showProcurement)
            .putBoolean(KEY_SHOW_INVENTORY, user.showInventory)
            .putBoolean(KEY_SHOW_SUPPLIER, user.showSupplier)
            .putBoolean(KEY_SHOW_REPORT, user.showReport)
            .putBoolean(KEY_SHOW_MARGIN, user.showMargin)
            .putBoolean(KEY_SHOW_SHEETS, user.showGoogleSheets)
            .putBoolean(KEY_SHOW_ADMIN_PANEL, user.isAdmin || user.showAdminPanel)

        if (rememberMe) {
            editor.putString(KEY_REMEMBERED_USERNAME, user.username.trim())
            editor.putString(KEY_REMEMBERED_PASSWORD, rawPassword.trim())
        } else {
            editor.remove(KEY_REMEMBERED_USERNAME)
            editor.remove(KEY_REMEMBERED_PASSWORD)
        }

        editor.apply()
    }

    /**
     * Retrieves currently active session if user is logged in and not logged out.
     */
    fun getSavedSession(context: Context): UserSession? {
        val prefs = getPrefs(context)
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        val username = prefs.getString(KEY_USERNAME, null)

        if (!isLoggedIn || username.isNullOrBlank()) {
            Log.d(TAG, "No active session found (isLoggedIn=$isLoggedIn)")
            return null
        }

        val session = UserSession(
            id = prefs.getLong(KEY_USER_ID, 0L),
            username = username,
            fullName = prefs.getString(KEY_FULL_NAME, "") ?: "",
            role = prefs.getString(KEY_ROLE, "User") ?: "User",
            isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false),
            canDeletePO = prefs.getBoolean(KEY_CAN_DELETE_PO, true),
            showProcurement = prefs.getBoolean(KEY_SHOW_PROCUREMENT, true),
            showInventory = prefs.getBoolean(KEY_SHOW_INVENTORY, true),
            showSupplier = prefs.getBoolean(KEY_SHOW_SUPPLIER, true),
            showReport = prefs.getBoolean(KEY_SHOW_REPORT, true),
            showMargin = prefs.getBoolean(KEY_SHOW_MARGIN, true),
            showGoogleSheets = prefs.getBoolean(KEY_SHOW_SHEETS, true),
            showAdminPanel = prefs.getBoolean(KEY_SHOW_ADMIN_PANEL, false),
            isLoggedIn = true
        )

        Log.i(TAG, "Restored active session for user: '${session.username}' (${session.role})")
        return session
    }

    /**
     * Updates cached session permissions with fresh UserAccountEntity from database.
     */
    fun updateSessionWithUser(context: Context, user: UserAccountEntity) {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_IS_LOGGED_IN, false)) return

        prefs.edit()
            .putLong(KEY_USER_ID, user.id)
            .putString(KEY_USERNAME, user.username)
            .putString(KEY_FULL_NAME, user.fullName)
            .putString(KEY_ROLE, user.role)
            .putBoolean(KEY_IS_ADMIN, user.isAdmin)
            .putBoolean(KEY_CAN_DELETE_PO, user.canDeletePO)
            .putBoolean(KEY_SHOW_PROCUREMENT, user.showProcurement)
            .putBoolean(KEY_SHOW_INVENTORY, user.showInventory)
            .putBoolean(KEY_SHOW_SUPPLIER, user.showSupplier)
            .putBoolean(KEY_SHOW_REPORT, user.showReport)
            .putBoolean(KEY_SHOW_MARGIN, user.showMargin)
            .putBoolean(KEY_SHOW_SHEETS, user.showGoogleSheets)
            .putBoolean(KEY_SHOW_ADMIN_PANEL, user.isAdmin || user.showAdminPanel)
            .apply()
    }

    /**
     * Explicitly logs out the user.
     * Clears active session so app will prompt for login.
     * If Remember Me was chosen, preserves pre-filled credentials for convenient re-login.
     */
    fun clearActiveSession(context: Context) {
        val prefs = getPrefs(context)
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        val rememberedUsername = prefs.getString(KEY_REMEMBERED_USERNAME, "") ?: ""
        val rememberedPassword = prefs.getString(KEY_REMEMBERED_PASSWORD, "") ?: ""

        Log.i(TAG, "User clicked Log Out. Terminating active session.")

        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .remove(KEY_USER_ID)
            .remove(KEY_USERNAME)
            .remove(KEY_FULL_NAME)
            .remove(KEY_ROLE)
            .remove(KEY_IS_ADMIN)
            .remove(KEY_CAN_DELETE_PO)
            .remove(KEY_SHOW_PROCUREMENT)
            .remove(KEY_SHOW_INVENTORY)
            .remove(KEY_SHOW_SUPPLIER)
            .remove(KEY_SHOW_REPORT)
            .remove(KEY_SHOW_MARGIN)
            .remove(KEY_SHOW_SHEETS)
            .remove(KEY_SHOW_ADMIN_PANEL)
            .apply()

        // Restore remember me credentials if user chose to remember them
        if (rememberMe) {
            prefs.edit()
                .putBoolean(KEY_REMEMBER_ME, true)
                .putString(KEY_REMEMBERED_USERNAME, rememberedUsername)
                .putString(KEY_REMEMBERED_PASSWORD, rememberedPassword)
                .apply()
        }
    }

    /**
     * Returns remembered credentials for the login screen.
     */
    fun getRememberedCredentials(context: Context): Triple<Boolean, String, String> {
        val prefs = getPrefs(context)
        val rememberMe = prefs.getBoolean(KEY_REMEMBER_ME, false)
        val username = if (rememberMe) prefs.getString(KEY_REMEMBERED_USERNAME, "") ?: "" else ""
        val password = if (rememberMe) prefs.getString(KEY_REMEMBERED_PASSWORD, "") ?: "" else ""
        return Triple(rememberMe, username, password)
    }
}
