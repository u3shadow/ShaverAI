package com.u3coding.shaver.device

import android.content.Context
import android.net.wifi.WifiManager

class WifiProvider(private val context: Context) {

    fun getCurrentWifiSsid(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val rawSsid = wifiManager.connectionInfo?.ssid ?: return null
            val ssid = rawSsid.removePrefix("\"").removeSuffix("\"")
            if (ssid.equals("<unknown ssid>", ignoreCase = true) || ssid.isBlank()) {
                null
            } else {
                ssid
            }
        } catch (_: Exception) {
            null
        }
    }
}
