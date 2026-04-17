package com.u3coding.shaver.ui.permission

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionRequestHelper(private val activity: AppCompatActivity) {

    fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            REQUEST_BLUETOOTH_CONNECT_PERMISSION
        )
    }

    fun hasWifiPermission(): Boolean {
        return requiredWifiPermissions().all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestWifiPermission() {
        ActivityCompat.requestPermissions(
            activity,
            requiredWifiPermissions(),
            REQUEST_WIFI_PERMISSION
        )
    }

    fun handlePermissionsResult(
        requestCode: Int,
        grantResults: IntArray,
        onBluetoothGranted: () -> Unit,
        onWifiGranted: () -> Unit,
        onWifiDenied: () -> Unit
    ): Boolean {
        if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onBluetoothGranted()
            }
            return true
        }

        if (requestCode == REQUEST_WIFI_PERMISSION) {
            val granted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                onWifiGranted()
            } else {
                onWifiDenied()
            }
            return true
        }

        return false
    }

    private fun requiredWifiPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasWriteSettingsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true
        }

        if (Settings.System.canWrite(activity)) {
            return true
        }

        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivity(intent)
        return false
    }

    companion object {
        const val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1002
        const val REQUEST_WIFI_PERMISSION = 1003
    }
}
