package com.u3coding.shaver

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Context.WIFI_SERVICE
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat

class WifiProvider(val context: Activity) {
  //获取当前wifi的ssid
    fun getCurrentWifiSSID(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(context, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
                return null
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(context, arrayOf(Manifest.permission.ACCESS_WIFI_STATE), 1)
                return null
            }
        }

        val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        return wifiInfo.ssid?.removePrefix("\"")?.removeSuffix("\"")
    }
}