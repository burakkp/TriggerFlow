package com.triggerflow

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("TriggerFlowPrefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TRUSTED_SSIDS = "trusted_ssids"
        private const val KEY_TUNNEL_NAME = "tunnel_name"
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_LAST_KNOWN_SSID = "last_known_ssid"
        private const val KEY_IS_PREMIUM = "is_premium"
        private const val KEY_PROMO_BYPASS = "promo_bypass"
        private const val KEY_ADBLOCK_TUNNEL_NAME = "adblock_tunnel_name"
        private const val KEY_IS_ADBLOCK_ENABLED = "is_adblock_enabled"
    }

    var trustedSsids: Set<String>
        get() = prefs.getStringSet(KEY_TRUSTED_SSIDS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_TRUSTED_SSIDS, value).apply()

    var tunnelName: String
        get() = prefs.getString(KEY_TUNNEL_NAME, "wg0") ?: "wg0"
        set(value) = prefs.edit().putString(KEY_TUNNEL_NAME, value).apply()

    var adBlockTunnelName: String
        get() = prefs.getString(KEY_ADBLOCK_TUNNEL_NAME, "wg0-adblock") ?: "wg0-adblock"
        set(value) = prefs.edit().putString(KEY_ADBLOCK_TUNNEL_NAME, value).apply()

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_IS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ENABLED, value).apply()
        
    var isAdBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_IS_ADBLOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_ADBLOCK_ENABLED, value).apply()
        
    // Volatile state, maybe better used with a service binding, but simple specific pref works for UI sync
    var isServiceRunning: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_RUNNING, value).apply()

    var lastKnownSsid: String
        get() = prefs.getString(KEY_LAST_KNOWN_SSID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_KNOWN_SSID, value).apply()

    var isPremium: Boolean
        get() = prefs.getBoolean(KEY_IS_PREMIUM, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_PREMIUM, value).apply()

    var isPromoBypassActive: Boolean
        get() = prefs.getBoolean(KEY_PROMO_BYPASS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PROMO_BYPASS, value).apply()
            // Also set master premium flag for easier checking
            if(value) isPremium = true
        }
        
    fun setPromoBypass(active: Boolean) {
        isPromoBypassActive = active
    }
}
