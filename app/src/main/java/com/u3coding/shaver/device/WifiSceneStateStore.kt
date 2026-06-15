package com.u3coding.shaver.device

import android.content.Context

class WifiSceneStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveLastConnectedSsid(ssid: String) {
        val cleanSsid = ssid.trim()
        if (cleanSsid.isBlank()) return
        preferences.edit().putString(KEY_LAST_CONNECTED_SSID, cleanSsid).apply()
    }

    fun getLastConnectedSsid(): String? {
        return preferences.getString(KEY_LAST_CONNECTED_SSID, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val PREF_NAME = "wifi_scene_state"
        private const val KEY_LAST_CONNECTED_SSID = "last_connected_ssid"
    }
}
