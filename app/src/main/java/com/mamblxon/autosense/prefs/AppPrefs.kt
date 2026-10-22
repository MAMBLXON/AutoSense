package com.mamblxon.autosense.prefs

import android.content.Context

class AppPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var currentProfileId: Long
        get() = sp.getLong(KEY_CURRENT_PROFILE_ID, 0L)
        set(value) { sp.edit().putLong(KEY_CURRENT_PROFILE_ID, value).apply() }

    /** Убирает указатель на активный профиль (после удаления последнего профиля / resetAll). */
    fun clearCurrentProfile() { currentProfileId = 0L }

    var yellowThreshold: Float
        get() = sp.getFloat(KEY_YELLOW_THRESHOLD, DEFAULT_YELLOW_THRESHOLD)
        set(value) { sp.edit().putFloat(KEY_YELLOW_THRESHOLD, value.coerceIn(0.5f, 0.99f)).apply() }

    var checkPeriodHours: Int
        get() = sp.getInt(KEY_PERIOD_HOURS, DEFAULT_PERIOD_HOURS)
        set(value) { sp.edit().putInt(KEY_PERIOD_HOURS, value.coerceIn(MIN_PERIOD_HOURS, MAX_PERIOD_HOURS)).apply() }

    var notificationsEnabled: Boolean
        get() = sp.getBoolean(KEY_NOTIF_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_NOTIF_ENABLED, value).apply()

    var themeMode: Int
        get() = sp.getInt(KEY_THEME_MODE, MODE_DARK)
        set(value) = sp.edit().putInt(KEY_THEME_MODE, value).apply()

    var notifPermissionAsked: Boolean
        get() = sp.getBoolean(KEY_NOTIF_ASKED, false)
        set(value) = sp.edit().putBoolean(KEY_NOTIF_ASKED, value).apply()

    // НОВОЕ: адаптивный режим (подстройка под стиль вождения)
    var adaptiveModeEnabled: Boolean
        get() = sp.getBoolean(KEY_ADAPTIVE_MODE, true)
        set(value) = sp.edit().putBoolean(KEY_ADAPTIVE_MODE, value).apply()

    var sortOrderName: String
        get() = sp.getString(KEY_SORT_ORDER, DEFAULT_SORT_ORDER) ?: DEFAULT_SORT_ORDER
        set(value) = sp.edit().putString(KEY_SORT_ORDER, value).apply()

    val sortOrder: com.mamblxon.autosense.ui.main.SortOrder
        get() = runCatching {
            com.mamblxon.autosense.ui.main.SortOrder.valueOf(sortOrderName)
        }.getOrDefault(com.mamblxon.autosense.ui.main.SortOrder.DATE_DESC)

    fun setSortOrder(order: com.mamblxon.autosense.ui.main.SortOrder) { sortOrderName = order.name }

    val typeFilterNames: Set<String>
        get() = sp.getString(KEY_TYPE_FILTER, "")
            ?.split(",")
            ?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            ?.toSet()
            ?: emptySet()

    fun setTypeFilter(names: Set<String>) { sp.edit().putString(KEY_TYPE_FILTER, names.joinToString(",")).apply() }

    fun getLastKm(profileId: Long): Int = sp.getInt("${KEY_LAST_KM}__$profileId", 0)
    fun setLastKm(profileId: Long, value: Int) { sp.edit().putInt("${KEY_LAST_KM}__$profileId", value).apply() }
    fun getLastKmUpdate(profileId: Long): Long = sp.getLong("${KEY_LAST_KM_UPDATE}__$profileId", 0L)
    fun setLastKmUpdate(profileId: Long, value: Long) { sp.edit().putLong("${KEY_LAST_KM_UPDATE}__$profileId", value).apply() }
    fun resolveKnownKm(profileId: Long, fallback: Int): Int {
        val stored = getLastKm(profileId)
        return if (stored > 0) stored else fallback
    }
    fun saveKnownKm(profileId: Long, km: Int) { setLastKm(profileId, km) }

    companion object {
        const val PREFS_NAME = "autosense_prefs"
        const val KEY_CURRENT_PROFILE_ID = "current_profile_id"
        const val KEY_YELLOW_THRESHOLD = "yellow_threshold"
        const val KEY_PERIOD_HOURS = "check_period_hours"
        const val KEY_NOTIF_ENABLED = "notifications_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_NOTIF_ASKED = "notif_permission_asked"
        const val KEY_ADAPTIVE_MODE = "adaptive_mode"
        const val KEY_LAST_KM = "last_km"
        const val KEY_LAST_KM_UPDATE = "last_km_update"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_TYPE_FILTER = "type_filter"
        const val DEFAULT_SORT_ORDER = "DATE_DESC"
        const val DEFAULT_YELLOW_THRESHOLD = 0.8f
        const val DEFAULT_PERIOD_HOURS = 24
        const val MIN_PERIOD_HOURS = 1
        const val MAX_PERIOD_HOURS = 168
        const val MODE_SYSTEM = -1
        const val MODE_LIGHT = 1
        const val MODE_DARK = 2
    }
}
