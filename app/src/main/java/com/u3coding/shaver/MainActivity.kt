package com.u3coding.shaver

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel by viewModels<ChatViewModel>()
    private var input: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val text = findViewById<TextView>(R.id.tvText)
        val et = findViewById<EditText>(R.id.etInput)
        val btn = findViewById<Button>(R.id.btnSend)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { list ->
                    text.text = list.joinToString("\n")
                }
            }
        }

        et.addTextChangedListener {
            input = it?.toString().orEmpty()
        }

        btn.setOnClickListener {
            if (input.isBlank()) {
                return@setOnClickListener
            }

            if (viewModel.requiresBluetoothPermission(input) && !hasBluetoothConnectPermission()) {
                requestBluetoothConnectPermission()
                return@setOnClickListener
            }

            viewModel.sendStreamMessage(input)
            et.text?.clear()
            input = ""
        }
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != REQUEST_BLUETOOTH_CONNECT_PERMISSION) {
            return
        }

        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            return
        }

        if (input.isNotBlank() && viewModel.requiresBluetoothPermission(input)) {
            viewModel.sendMessage(input)
        }
    }

    companion object {
        private const val REQUEST_BLUETOOTH_CONNECT_PERMISSION = 1002
    }
}
