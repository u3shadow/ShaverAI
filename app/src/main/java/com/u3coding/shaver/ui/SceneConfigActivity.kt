package com.u3coding.shaver.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.u3coding.shaver.R
import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.action.rule.RuleEventType
import com.u3coding.shaver.action.rule.RuleRepo
import com.u3coding.shaver.action.rule.WifiAliasRepo

class SceneConfigActivity : AppCompatActivity() {
    private lateinit var ssid: String
    private lateinit var spEventType: Spinner
    private lateinit var tvSceneSsid: TextView
    private lateinit var tvAliasMissingHint: TextView
    private lateinit var etSceneAlias: EditText
    private lateinit var tvBrightnessLabel: TextView
    private lateinit var tvVolumeLabel: TextView
    private lateinit var sbBrightness: SeekBar
    private lateinit var sbVolume: SeekBar
    private lateinit var btnBrightnessKeep: Button
    private lateinit var btnBrightnessConfig: Button
    private lateinit var btnVolumeKeep: Button
    private lateinit var btnVolumeConfig: Button
    private lateinit var btnBluetoothKeep: Button
    private lateinit var btnBluetoothOff: Button
    private lateinit var btnBluetoothOn: Button

    private var brightnessEnabled = false
    private var volumeEnabled = false
    private var bluetoothState = BLUETOOTH_KEEP
    private var currentEventType = RuleEventType.WIFI_CONNECTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scene_config)

        RuleRepo.init(applicationContext)
        ssid = intent.getStringExtra(EXTRA_SSID)?.trim().orEmpty()
        if (ssid.isBlank()) {
            finish()
            return
        }

        bindViews()
        setupEventSpinner()
        setupClicks()
        loadCurrentEventRules()
    }

    private fun bindViews() {
        tvSceneSsid = findViewById(R.id.tvSceneSsid)
        tvAliasMissingHint = findViewById(R.id.tvAliasMissingHint)
        etSceneAlias = findViewById(R.id.etSceneAlias)
        tvSceneSsid.text = "\u65e0\u7ebf\u7f51\u540d\u79f0\uff1a$ssid"
        val alias = WifiAliasRepo.findAliasBySsid(ssid).orEmpty()
        findViewById<TextView>(R.id.tvSceneTitle).text = alias.ifBlank { ssid }
        etSceneAlias.setText(alias)
        updateAliasHint(alias)
        spEventType = findViewById(R.id.spEventType)
        tvBrightnessLabel = findViewById(R.id.tvBrightnessLabel)
        tvVolumeLabel = findViewById(R.id.tvVolumeLabel)
        sbBrightness = findViewById(R.id.sbBrightness)
        sbVolume = findViewById(R.id.sbVolume)
        btnBrightnessKeep = findViewById(R.id.btnBrightnessKeep)
        btnBrightnessConfig = findViewById(R.id.btnBrightnessConfig)
        btnVolumeKeep = findViewById(R.id.btnVolumeKeep)
        btnVolumeConfig = findViewById(R.id.btnVolumeConfig)
        btnBluetoothKeep = findViewById(R.id.btnBluetoothKeep)
        btnBluetoothOff = findViewById(R.id.btnBluetoothOff)
        btnBluetoothOn = findViewById(R.id.btnBluetoothOn)
        etSceneAlias.addTextChangedListener {
            updateAliasHint(it?.toString().orEmpty())
        }
    }

    private fun setupEventSpinner() {
        val adapter = ArrayAdapter(
            this,
            R.layout.item_scene_spinner,
            listOf("\u8fdb\u5165\u65e0\u7ebf\u7f51\u65f6", "\u79bb\u5f00\u65e0\u7ebf\u7f51\u65f6")
        )
        adapter.setDropDownViewResource(R.layout.item_scene_spinner_dropdown)
        spEventType.adapter = adapter
        spEventType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                currentEventType = if (position == 0) {
                    RuleEventType.WIFI_CONNECTED
                } else {
                    RuleEventType.WIFI_DISCONNECTED
                }
                loadCurrentEventRules()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun setupClicks() {
        btnBrightnessKeep.setOnClickListener {
            brightnessEnabled = false
            refreshState()
        }
        btnBrightnessConfig.setOnClickListener {
            brightnessEnabled = true
            refreshState()
        }
        btnVolumeKeep.setOnClickListener {
            volumeEnabled = false
            refreshState()
        }
        btnVolumeConfig.setOnClickListener {
            volumeEnabled = true
            refreshState()
        }
        btnBluetoothKeep.setOnClickListener {
            bluetoothState = BLUETOOTH_KEEP
            refreshState()
        }
        btnBluetoothOff.setOnClickListener {
            bluetoothState = BLUETOOTH_OFF
            refreshState()
        }
        btnBluetoothOn.setOnClickListener {
            bluetoothState = BLUETOOTH_ON
            refreshState()
        }
        findViewById<Button>(R.id.btnSaveScene).setOnClickListener {
            saveCurrentEventRules()
        }
        findViewById<Button>(R.id.btnSaveAlias).setOnClickListener {
            saveAlias()
        }
        findViewById<Button>(R.id.btnDeleteScene).setOnClickListener {
            confirmDeleteScene()
        }
        sbBrightness.setOnSeekBarChangeListener(labelUpdater { updateLabels() })
        sbVolume.setOnSeekBarChangeListener(labelUpdater { updateLabels() })
    }

    private fun loadCurrentEventRules() {
        brightnessEnabled = false
        volumeEnabled = false
        bluetoothState = BLUETOOTH_KEEP
        sbBrightness.progress = 50
        sbVolume.progress = 50

        RuleRepo.getRules(ssid, currentEventType).forEach { action ->
            when (action.operation) {
                "set_brightness" -> {
                    brightnessEnabled = true
                    sbBrightness.progress = action.intValue(defaultValue = 50)
                }
                "set_volume" -> {
                    volumeEnabled = true
                    sbVolume.progress = action.intValue(defaultValue = 50)
                }
                "open_bluetooth" -> bluetoothState = BLUETOOTH_ON
                "close_bluetooth" -> bluetoothState = BLUETOOTH_OFF
            }
        }
        refreshState()
    }

    private fun saveCurrentEventRules() {
        val actions = mutableListOf<Action>()
        if (brightnessEnabled) {
            actions += Action(
                trigger = ssid,
                operation = "set_brightness",
                params = mapOf("value" to sbBrightness.progress),
                eventType = currentEventType
            )
        }
        if (volumeEnabled) {
            actions += Action(
                trigger = ssid,
                operation = "set_volume",
                params = mapOf("value" to sbVolume.progress),
                eventType = currentEventType
            )
        }
        when (bluetoothState) {
            BLUETOOTH_ON -> actions += Action(ssid, "open_bluetooth", emptyMap(), currentEventType)
            BLUETOOTH_OFF -> actions += Action(ssid, "close_bluetooth", emptyMap(), currentEventType)
        }
        RuleRepo.replaceRules(ssid, currentEventType, actions)
        if (etSceneAlias.text?.toString()?.trim().isNullOrBlank()) {
            Toast.makeText(this, "\u5efa\u8bae\u7ed9\u65e0\u7ebf\u7f51\u8bbe\u7f6e\u522b\u540d\uff0c\u4ee5\u540e\u53ef\u4ee5\u76f4\u63a5\u5bf9\u667a\u80fd\u52a9\u624b\u8bf4\u201c\u5bb6\u201d\u6216\u201c\u516c\u53f8\u201d", Toast.LENGTH_LONG).show()
        }
        Toast.makeText(this, "\u914d\u7f6e\u5df2\u4fdd\u5b58", Toast.LENGTH_SHORT).show()
    }

    private fun saveAlias() {
        val alias = etSceneAlias.text?.toString()?.trim().orEmpty()
        if (alias.isBlank()) {
            Toast.makeText(this, "\u8bf7\u8f93\u5165\u522b\u540d", Toast.LENGTH_SHORT).show()
            return
        }
        WifiAliasRepo.saveAlias(ssid, alias)
        findViewById<TextView>(R.id.tvSceneTitle).text = alias
        updateAliasHint(alias)
        Toast.makeText(this, "\u522b\u540d\u5df2\u4fdd\u5b58", Toast.LENGTH_SHORT).show()
    }

    private fun updateAliasHint(alias: String) {
        tvAliasMissingHint.visibility = if (alias.trim().isBlank()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }
    }

    private fun confirmDeleteScene() {
        AlertDialog.Builder(this)
            .setTitle("\u5220\u9664\u573a\u666f")
            .setMessage("\u786e\u5b9a\u5220\u9664 $ssid \u7684\u6240\u6709\u914d\u7f6e\u5417\uff1f")
            .setPositiveButton("\u5220\u9664") { _, _ ->
                RuleRepo.deleteScene(ssid)
                Toast.makeText(this, "\u573a\u666f\u5df2\u5220\u9664", Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("\u53d6\u6d88", null)
            .show()
    }

    private fun refreshState() {
        applyButtonStyle(btnBrightnessKeep, !brightnessEnabled)
        applyButtonStyle(btnBrightnessConfig, brightnessEnabled)
        applyButtonStyle(btnVolumeKeep, !volumeEnabled)
        applyButtonStyle(btnVolumeConfig, volumeEnabled)
        applyButtonStyle(btnBluetoothKeep, bluetoothState == BLUETOOTH_KEEP)
        applyButtonStyle(btnBluetoothOff, bluetoothState == BLUETOOTH_OFF)
        applyButtonStyle(btnBluetoothOn, bluetoothState == BLUETOOTH_ON)

        sbBrightness.isEnabled = brightnessEnabled
        sbBrightness.alpha = if (brightnessEnabled) 1f else 0.45f
        sbVolume.isEnabled = volumeEnabled
        sbVolume.alpha = if (volumeEnabled) 1f else 0.45f
        updateLabels()
    }

    private fun updateLabels() {
        tvBrightnessLabel.text = "\u4eae\u5ea6 ${sbBrightness.progress}%"
        tvVolumeLabel.text = "\u97f3\u91cf ${sbVolume.progress}%"
    }

    private fun applyButtonStyle(button: Button, selected: Boolean) {
        button.setBackgroundResource(if (selected) R.drawable.bg_option_active else R.drawable.bg_option_inactive)
        button.setTextColor(if (selected) 0xFFFFFFFF.toInt() else 0xFFCDEBFF.toInt())
        button.alpha = if (selected) 1f else 0.82f
        button.setTypeface(button.typeface, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun labelUpdater(onChanged: () -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private fun Action.intValue(defaultValue: Int): Int {
        return when (val value = params["value"]) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull() ?: defaultValue
            else -> defaultValue
        }.coerceIn(0, 100)
    }

    companion object {
        const val EXTRA_SSID = "extra_ssid"
        private const val BLUETOOTH_KEEP = 0
        private const val BLUETOOTH_OFF = 1
        private const val BLUETOOTH_ON = 2
    }
}
