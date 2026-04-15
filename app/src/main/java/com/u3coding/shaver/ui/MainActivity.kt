package com.u3coding.shaver.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.u3coding.shaver.R
import com.u3coding.shaver.device.WifiProvider
import com.u3coding.shaver.model.Role
import com.u3coding.shaver.ui.chat.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<ChatViewModel>()
    private lateinit var wifiProvider: WifiProvider
    private lateinit var inputEditText: EditText
    private var input: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        wifiProvider = WifiProvider(this)

        val text = findViewById<TextView>(R.id.tvText)
        text.movementMethod = ScrollingMovementMethod()
        val et = findViewById<EditText>(R.id.etInput)
        inputEditText = et
        val btn = findViewById<Button>(R.id.btnSend)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { list ->
                    text.text = list.joinToString("\n") { message ->
                        val contentWithEnv = if (message.role == Role.USER) {
                            val env = message.wifiSsid ?: "unknown"
                            "${message.content} [env: $env]"
                        } else {
                            message.content
                        }
                        "${message.role}: $contentWithEnv"
                    }
                }
            }
        }

        et.addTextChangedListener {
            input = it?.toString().orEmpty()
        }

        btn.setOnClickListener {
            trySendMessage()
        }
    }

    private fun trySendMessage() {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }

        if (viewModel.requiresBluetoothPermission(message) && !hasBluetoothConnectPermission()) {
            requestBluetoothConnectPermission()
            return
        }

        if (!hasWifiPermission()) {
            Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT).show()
            requestWifiPermission()
            return
        }

        val ssid = wifiProvider.getCurrentWifiSsid()
        viewModel.sendStreamMessage(message, ssid)
        inputEditText.text?.clear()
        input = ""
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
            REQUEST_BLUETOOTH_CONNECT_PERMISSION
        )
    }

    private fun hasWifiPermission(): Boolean {
        return requiredWifiPermissions().all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
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

    private fun requestWifiPermission() {
        ActivityCompat.requestPermissions(
            this,
            requiredWifiPermissions(),
            REQUEST_WIFI_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                trySendMessage()
            }
            return
        }

        if (requestCode == REQUEST_WIFI_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (granted) {
                trySendMessage()
            } else {
                Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1002
        private const val REQUEST_WIFI_PERMISSION = 1003
    }
}
