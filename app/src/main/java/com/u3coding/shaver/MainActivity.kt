package com.u3coding.shaver

import android.app.Activity
import android.app.AlertDialog
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var settingsDbHelper: SettingsDbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        settingsDbHelper = SettingsDbHelper(this)

        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            showSettingsDialog()
        }
    }

    override fun onDestroy() {
        settingsDbHelper.close()
        super.onDestroy()
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        val wifiIdEditText = view.findViewById<EditText>(R.id.etWifiId)
        val notificationVolumeSeekBar = view.findViewById<SeekBar>(R.id.sbNotificationVolume)
        val ringtoneVolumeSeekBar = view.findViewById<SeekBar>(R.id.sbRingtoneVolume)
        val brightnessSeekBar = view.findViewById<SeekBar>(R.id.sbBrightness)
        val notificationVolumeValue = view.findViewById<TextView>(R.id.tvNotificationVolumeValue)
        val ringtoneVolumeValue = view.findViewById<TextView>(R.id.tvRingtoneVolumeValue)
        val brightnessValue = view.findViewById<TextView>(R.id.tvBrightnessValue)
        val autoBrightnessSwitch = view.findViewById<Switch>(R.id.swAutoBrightness)
        val readCurrentWifiButton = view.findViewById<Button>(R.id.btnReadCurrentWifi)

        val savedSettings = settingsDbHelper.loadSettings() ?: DeviceSettings.DEFAULT
        wifiIdEditText.setText(savedSettings.wifiId)
        notificationVolumeSeekBar.progress = savedSettings.notificationVolume
        ringtoneVolumeSeekBar.progress = savedSettings.ringtoneVolume
        brightnessSeekBar.progress = savedSettings.screenBrightness
        autoBrightnessSwitch.isChecked = savedSettings.autoBrightness

        updateProgressLabel(notificationVolumeValue, notificationVolumeSeekBar.progress)
        updateProgressLabel(ringtoneVolumeValue, ringtoneVolumeSeekBar.progress)
        updateProgressLabel(brightnessValue, brightnessSeekBar.progress)

        notificationVolumeSeekBar.attachProgressLabel(notificationVolumeValue)
        ringtoneVolumeSeekBar.attachProgressLabel(ringtoneVolumeValue)
        brightnessSeekBar.attachProgressLabel(brightnessValue)

        if (wifiIdEditText.text.isBlank()) {
            getCurrentWifiId()?.let { wifiIdEditText.setText(it) }
        }

        readCurrentWifiButton.setOnClickListener {
            val currentWifiId = getCurrentWifiId()
            if (currentWifiId.isNullOrBlank()) {
                Toast.makeText(this, getString(R.string.read_wifi_failed), Toast.LENGTH_SHORT).show()
            } else {
                wifiIdEditText.setText(currentWifiId)
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val settings = DeviceSettings(
                    wifiId = wifiIdEditText.text.toString().trim(),
                    notificationVolume = notificationVolumeSeekBar.progress,
                    ringtoneVolume = ringtoneVolumeSeekBar.progress,
                    screenBrightness = brightnessSeekBar.progress,
                    autoBrightness = autoBrightnessSwitch.isChecked
                )
                settingsDbHelper.saveSettings(settings)
                Toast.makeText(this, getString(R.string.save_success), Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updateProgressLabel(label: TextView, value: Int) {
        label.text = getString(R.string.progress_percent, value)
    }

    private fun SeekBar.attachProgressLabel(label: TextView) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateProgressLabel(label, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
    }

    private fun getCurrentWifiId(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ssid = wifiManager.connectionInfo?.ssid ?: return null
            val normalized = ssid.removePrefix("\"").removeSuffix("\"")
            if (
                normalized.equals("<unknown ssid>", ignoreCase = true) ||
                normalized.equals("unknown ssid", ignoreCase = true)
            ) {
                null
            } else {
                normalized
            }
        } catch (_: Exception) {
            null
        }
    }
}
