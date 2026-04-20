package com.u3coding.shaver.ui

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.u3coding.shaver.R
import com.u3coding.shaver.action.Action
import com.u3coding.shaver.action.ActionExecutor
import com.u3coding.shaver.action.RuleEngine
import com.u3coding.shaver.action.RuleRunResult
import com.u3coding.shaver.device.WifiProvider
import com.u3coding.shaver.model.Role
import com.u3coding.shaver.ui.chat.ChatViewModel
import com.u3coding.shaver.ui.chat.ChatViewModelFactory
import com.u3coding.shaver.ui.permission.PermissionRequestHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {


    private lateinit var executor: ActionExecutor
    private  var lastSSID: String = ""
    private lateinit var viewModel: ChatViewModel
    private lateinit var wifiProvider: WifiProvider
    private lateinit var permissionHelper: PermissionRequestHelper
    private lateinit var inputEditText: EditText
    private var input: String = ""
    private lateinit var ruleEngine : RuleEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        ruleEngine = RuleEngine(this)
        executor = ActionExecutor(applicationContext)
        val factory = ChatViewModelFactory(executor)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        wifiProvider = WifiProvider(this)
        permissionHelper = PermissionRequestHelper(this)
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

        if (viewModel.requiresBluetoothPermission(message) && !permissionHelper.hasBluetoothConnectPermission()) {
            permissionHelper.requestBluetoothConnectPermission()
            return
        }
        if (!permissionHelper.hasWriteSettingsPermission()) {
            Toast.makeText(this, getString(R.string.write_settings_not_granted), Toast.LENGTH_SHORT).show()
            return
        }
        if (!permissionHelper.hasWifiPermission()) {
            Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT)
                .show()
            permissionHelper.requestWifiPermission()
            return
        }

        val ssid = wifiProvider.getCurrentWifiSsid()
        checkAndApplyWifiScene(ssid?:"")

        viewModel.sendStreamMessage(message, ssid)
        inputEditText.text?.clear()
        input = ""
    }

    fun checkAndApplyWifiScene(ssid:String){
        val result =ruleEngine.run(ssid)
        var resultString = ""
        when(result){
            is RuleRunResult.Success -> {
              resultString = "执行成功"
            }
            is RuleRunResult.NoRule -> {
                resultString = "未找到对应规则"
            }
             is RuleRunResult.SkippedDuplicate -> {
                 resultString = "已经执行过相同规则，跳过"
             }
            is RuleRunResult.Failed -> {
                resultString = "执行失败，原因：${result.reason}"
             }
        }
        Toast.makeText(this, resultString, Toast.LENGTH_SHORT).show()
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionHelper.handlePermissionsResult(
            requestCode = requestCode,
            grantResults = grantResults,
            onBluetoothGranted = { trySendMessage() },
            onWifiGranted = { trySendMessage() },
            onWifiDenied = {
                Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT)
                    .show()
            }
        )
    }
}
