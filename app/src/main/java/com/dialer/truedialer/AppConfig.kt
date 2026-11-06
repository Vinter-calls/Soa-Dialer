package com.dialer.truedialer

import android.content.Context

/**
 * App Configuration
 *
 * Link to the backend database will be provided later by the user.
 * You can switch [USE_MOCK_DATABASE] to false and enter your API / Database URL below.
 * Settings are persisted across app restarts via SharedPreferences.
 */
object AppConfig {

    private const val PREFS_NAME = "truedialer_preferences"
    private const val KEY_AUTO_CALL_RECORDING = "auto_call_recording"
    private const val KEY_AUTO_SYNC_MONGODB = "auto_sync_mongodb"
    private const val KEY_USE_MOCK_DB = "use_mock_db"
    private const val KEY_API_URL = "backend_api_url"
    private const val KEY_API_KEY = "backend_api_key"
    private const val KEY_CRM_WEBHOOK_URL = "crm_webhook_url"
    private const val KEY_CRM_WEBHOOK_TOKEN = "crm_webhook_token"
    private const val KEY_AUTO_REJECT_SPAM = "auto_reject_spam"
    private const val KEY_SHOW_POST_CALL = "show_post_call"
    private const val KEY_CENTER_ID = "center_id"
    private const val KEY_CENTER_NAME = "center_name"
    private const val KEY_CURRENT_USER_ID = "current_user_id"
    private const val KEY_CURRENT_USER_NAME = "current_user_name"
    private const val KEY_CURRENT_USER_ROLE = "current_user_role"
    private const val KEY_CURRENT_USER_EMAIL = "current_user_email"
    private const val KEY_CURRENT_USER_USERNAME = "current_user_username"
    private const val KEY_IS_LOGGED_IN = "is_crm_logged_in"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_NOTIF_INCOMING = "notif_incoming"
    private const val KEY_NOTIF_MISSED = "notif_missed"
    private const val KEY_NOTIF_FOLLOW_UPS = "notif_follow_ups"
    private const val KEY_NOTIF_DAILY_TASKS = "notif_daily_tasks"
    private const val KEY_WHATSAPP_TEMPLATE = "whatsapp_template"

    const val DEFAULT_WHATSAPP_TEMPLATE = "Greetings from School of athens."
    const val DEFAULT_WHATSAPP_CALL_TEMPLATE = DEFAULT_WHATSAPP_TEMPLATE

    /**
     * In-Call quick WhatsApp message template (configurable from Settings)
     */
    var WHATSAPP_TEMPLATE: String = DEFAULT_WHATSAPP_TEMPLATE
    var WHATSAPP_CALL_TEMPLATE: String
        get() = WHATSAPP_TEMPLATE
        set(value) { WHATSAPP_TEMPLATE = value }

    // --- THEME & NOTIFICATIONS ---
    var THEME_MODE: String = "SYSTEM" // "SYSTEM", "LIGHT", "DARK"
    var NOTIF_INCOMING_CALL: Boolean = true
    var NOTIF_MISSED_CALL: Boolean = true
    var NOTIF_FOLLOW_UPS: Boolean = true
    var NOTIF_DAILY_TASKS: Boolean = true

    // --- CENTER & USER IDENTIFICATION (1-Center-1-Phone) ---
    var CENTER_ID: String = "67cd080a040cb823ea6c0744"
    var CENTER_NAME: String = "SOA-Whitefield"

    var CURRENT_USER_ID: String = ""
    var CURRENT_USER_NAME: String = "Not Logged In"
    var CURRENT_USER_ROLE: String = "NONE"
    var CURRENT_USER_EMAIL: String = ""
    var CURRENT_USER_USERNAME: String = ""

    val isLoggedIn: Boolean
        get() = CURRENT_USER_ID.isNotBlank() && !CURRENT_USER_ROLE.equals("NONE", ignoreCase = true)

    val isCurrentUserManager: Boolean
        get() = isLoggedIn && (CURRENT_USER_ROLE.contains("MANAGER", ignoreCase = true) || CURRENT_USER_ROLE.equals("ADMIN", ignoreCase = true))

    /**
     * Automatically start recording when a call is connected / active
     */
    var AUTO_CALL_RECORDING: Boolean = false

    /**
     * Automatically sync leads to MongoDB Atlas when created or updated
     */
    var AUTO_SYNC_MONGODB: Boolean = true

    /**
     * Disabled mock database by default to ensure real live data is used.
     */
    var USE_MOCK_DATABASE: Boolean = false

    var BACKEND_API_BASE_URL: String = ""

    /**
     * API Key or Authorization Bearer token for your backend database (if required).
     */
    var BACKEND_API_KEY: String = ""

    /**
     * Endpoint path for searching enquiry by phone number.
     * Appends query param: e.g. /enquiries/lookup?phone=+1234567890
     */
    var ENQUIRY_LOOKUP_ENDPOINT: String = "/enquiries/lookup"

    /**
     * Endpoint path for fetching all enquiries list.
     */
    var ENQUIRY_LIST_ENDPOINT: String = "/enquiries"

    /**
     * Default country code prefix if phone number entered without country code (e.g. "+91" for India, "+1" for US)
     */
    var DEFAULT_COUNTRY_CODE: String = "+91"

    // --- FEATURE 8: CRM & WEBHOOK SYNC CONFIG ---

    /**
     * Webhook URL to push call logs, lead updates, and notes.
     * (e.g. Google Sheets Apps Script Webhook, Make.com, Zapier, Airtable, HubSpot, Custom CRM)
     */
    var CRM_WEBHOOK_URL: String = ""

    /**
     * Optional Bearer Token / Secret for your CRM Webhook endpoint
     */
    var CRM_WEBHOOK_BEARER_TOKEN: String = ""

    // --- FEATURE 6: SPAM FILTERING ---

    /**
     * Automatically silence or reject calls from numbers flagged as Spam
     */
    var AUTO_REJECT_SPAM: Boolean = false

    // --- FEATURE 1: POST-CALL ACTION SCREEN ---

    /**
     * Automatically show post-call action screen when a phone call terminates
     */
    var SHOW_POST_CALL_DIALOG: Boolean = true

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        AUTO_CALL_RECORDING = prefs.getBoolean(KEY_AUTO_CALL_RECORDING, false)
        AUTO_SYNC_MONGODB = prefs.getBoolean(KEY_AUTO_SYNC_MONGODB, true)
        USE_MOCK_DATABASE = prefs.getBoolean(KEY_USE_MOCK_DB, false)
        BACKEND_API_BASE_URL = prefs.getString(KEY_API_URL, "") ?: ""
        BACKEND_API_KEY = prefs.getString(KEY_API_KEY, "") ?: ""
        CRM_WEBHOOK_URL = prefs.getString(KEY_CRM_WEBHOOK_URL, "") ?: ""
        CRM_WEBHOOK_BEARER_TOKEN = prefs.getString(KEY_CRM_WEBHOOK_TOKEN, "") ?: ""
        AUTO_REJECT_SPAM = prefs.getBoolean(KEY_AUTO_REJECT_SPAM, false)
        SHOW_POST_CALL_DIALOG = prefs.getBoolean(KEY_SHOW_POST_CALL, true)
        CENTER_ID = prefs.getString(KEY_CENTER_ID, "67cd080a040cb823ea6c0744") ?: "67cd080a040cb823ea6c0744"
        CENTER_NAME = prefs.getString(KEY_CENTER_NAME, "SOA-Whitefield") ?: "SOA-Whitefield"
        val hasLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (hasLoggedIn) {
            CURRENT_USER_ID = prefs.getString(KEY_CURRENT_USER_ID, "") ?: ""
            CURRENT_USER_NAME = prefs.getString(KEY_CURRENT_USER_NAME, "Not Logged In") ?: "Not Logged In"
            CURRENT_USER_ROLE = prefs.getString(KEY_CURRENT_USER_ROLE, "NONE") ?: "NONE"
            CURRENT_USER_EMAIL = prefs.getString(KEY_CURRENT_USER_EMAIL, "") ?: ""
            CURRENT_USER_USERNAME = prefs.getString(KEY_CURRENT_USER_USERNAME, "") ?: ""
        } else {
            CURRENT_USER_ID = ""
            CURRENT_USER_NAME = "Not Logged In"
            CURRENT_USER_ROLE = "NONE"
            CURRENT_USER_EMAIL = ""
            CURRENT_USER_USERNAME = ""
        }
        THEME_MODE = prefs.getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
        NOTIF_INCOMING_CALL = prefs.getBoolean(KEY_NOTIF_INCOMING, true)
        NOTIF_MISSED_CALL = prefs.getBoolean(KEY_NOTIF_MISSED, true)
        NOTIF_FOLLOW_UPS = prefs.getBoolean(KEY_NOTIF_FOLLOW_UPS, true)
        NOTIF_DAILY_TASKS = prefs.getBoolean(KEY_NOTIF_DAILY_TASKS, true)
        WHATSAPP_TEMPLATE = prefs.getString(KEY_WHATSAPP_TEMPLATE, DEFAULT_WHATSAPP_TEMPLATE) ?: DEFAULT_WHATSAPP_TEMPLATE
    }

    fun setWhatsAppTemplate(context: Context, template: String) {
        val sanitized = if (template.isBlank()) DEFAULT_WHATSAPP_TEMPLATE else template
        WHATSAPP_TEMPLATE = sanitized
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WHATSAPP_TEMPLATE, sanitized)
            .apply()
    }

    fun setThemeMode(context: Context, mode: String) {
        THEME_MODE = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putString(KEY_THEME_MODE, mode).apply()
    }

    fun setNotificationPreferences(context: Context, incoming: Boolean, missed: Boolean, followUps: Boolean, dailyTasks: Boolean) {
        NOTIF_INCOMING_CALL = incoming
        NOTIF_MISSED_CALL = missed
        NOTIF_FOLLOW_UPS = followUps
        NOTIF_DAILY_TASKS = dailyTasks
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_NOTIF_INCOMING, incoming)
            .putBoolean(KEY_NOTIF_MISSED, missed)
            .putBoolean(KEY_NOTIF_FOLLOW_UPS, followUps)
            .putBoolean(KEY_NOTIF_DAILY_TASKS, dailyTasks)
            .apply()
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
        setCurrentUser(context, "", "Not Logged In", "NONE", "", "")
    }

    fun setCenterConfig(context: Context, centerId: String, centerName: String) {
        CENTER_ID = centerId
        CENTER_NAME = centerName
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CENTER_ID, centerId)
            .putString(KEY_CENTER_NAME, centerName)
            .apply()
    }

    fun resolveCenterName(centerId: String, userName: String = "", email: String = ""): String {
        val uLower = userName.lowercase()
        val eLower = email.lowercase()
        return when {
            centerId == "67cd080a040cb823ea6c0744" || uLower.contains("whitefield") || eLower.contains("whitefield") -> "Whitefield"
            centerId == "67c00eb2073609b23054ca01" || uLower.contains("brookfield") || eLower.contains("brookfield") -> "Brookfield"
            uLower.contains("manager") -> {
                val clean = userName.replace("Manager", "", ignoreCase = true).trim()
                if (clean.isNotBlank()) clean.replaceFirstChar { it.uppercase() } else "School of Athens"
            }
            centerId == "6a70cad580c43932de3f7c8d" -> "School of Athens"
            else -> if (CENTER_NAME.isNotBlank() && CENTER_NAME != "Main Branch" && CENTER_NAME != "SOA-Whitefield") CENTER_NAME else "Whitefield"
        }
    }

    fun setCurrentUser(
        context: Context,
        userId: String,
        userName: String,
        role: String,
        email: String = "",
        username: String = "",
        centerId: String = "",
        centerName: String = ""
    ) {
        CURRENT_USER_ID = userId
        CURRENT_USER_NAME = userName
        CURRENT_USER_ROLE = role
        CURRENT_USER_EMAIL = email
        CURRENT_USER_USERNAME = username.ifBlank { userName }

        val loggedIn = userId.isNotBlank() && !role.equals("NONE", ignoreCase = true)
        if (loggedIn) {
            val resolvedCenterId = centerId.ifBlank { CENTER_ID }
            val resolvedCenterName = if (centerName.isNotBlank()) centerName else resolveCenterName(resolvedCenterId, userName, email)
            CENTER_ID = resolvedCenterId
            CENTER_NAME = resolvedCenterName
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            .putString(KEY_CURRENT_USER_ID, userId)
            .putString(KEY_CURRENT_USER_NAME, userName)
            .putString(KEY_CURRENT_USER_ROLE, role)
            .putString(KEY_CURRENT_USER_EMAIL, email)
            .putString(KEY_CURRENT_USER_USERNAME, username.ifBlank { userName })
            .apply {
                if (loggedIn) {
                    putString(KEY_CENTER_ID, CENTER_ID)
                    putString(KEY_CENTER_NAME, CENTER_NAME)
                }
            }
            .apply()
    }

    fun setAutoCallRecording(context: Context, enabled: Boolean) {
        AUTO_CALL_RECORDING = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_CALL_RECORDING, enabled).apply()
    }

    fun setAutoSyncMongodb(context: Context, enabled: Boolean) {
        AUTO_SYNC_MONGODB = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_SYNC_MONGODB, enabled).apply()
    }

    fun setUseMockDatabase(context: Context, useMock: Boolean) {
        USE_MOCK_DATABASE = useMock
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_USE_MOCK_DB, useMock).apply()
    }

    fun setBackendApiConfig(context: Context, url: String, key: String) {
        BACKEND_API_BASE_URL = url
        BACKEND_API_KEY = key
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_API_URL, url)
            .putString(KEY_API_KEY, key)
            .apply()
    }

    fun setCrmWebhookConfig(context: Context, url: String, token: String) {
        CRM_WEBHOOK_URL = url
        CRM_WEBHOOK_BEARER_TOKEN = token
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CRM_WEBHOOK_URL, url)
            .putString(KEY_CRM_WEBHOOK_TOKEN, token)
            .apply()
    }

    fun setAutoRejectSpam(context: Context, enabled: Boolean) {
        AUTO_REJECT_SPAM = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_REJECT_SPAM, enabled).apply()
    }

    fun setShowPostCallDialog(context: Context, enabled: Boolean) {
        SHOW_POST_CALL_DIALOG = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_SHOW_POST_CALL, enabled).apply()
    }
}
