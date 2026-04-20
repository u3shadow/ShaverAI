package com.u3coding.shaver.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.u3coding.shaver.R
import com.u3coding.shaver.action.Action
import com.u3coding.shaver.action.ActionExecutor
import com.u3coding.shaver.action.RuleEngine
import com.u3coding.shaver.action.RuleRunResult
import com.u3coding.shaver.device.WifiProvider
import com.u3coding.shaver.ui.adapter.ChatMessageAdapter
import com.u3coding.shaver.ui.adapter.EnvConfigAdapter
import com.u3coding.shaver.ui.chat.ChatViewModel
import com.u3coding.shaver.ui.chat.ChatViewModelFactory
import com.u3coding.shaver.ui.model.EnvConfigItem
import com.u3coding.shaver.ui.permission.PermissionRequestHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var executor: ActionExecutor
    private lateinit var viewModel: ChatViewModel
    private lateinit var wifiProvider: WifiProvider
    private lateinit var permissionHelper: PermissionRequestHelper
    private lateinit var inputEditText: EditText
    private lateinit var ruleEngine: RuleEngine

    private lateinit var tvWifiStatus: TextView
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var envConfigAdapter: EnvConfigAdapter

    private var input: String = ""
    private val envState = linkedMapOf(
        "Wi-Fi" to "unknown",
        "亮度" to "unknown",
        "音量" to "unknown",
        "蓝牙" to "unknown",
        "规则状态" to "未执行"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        ruleEngine = RuleEngine(this)
        executor = ActionExecutor(applicationContext)
        val factory = ChatViewModelFactory(executor)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        wifiProvider = WifiProvider(this)
        permissionHelper = PermissionRequestHelper(this)

        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        val et = findViewById<EditText>(R.id.etInput)
        inputEditText = et
        val btn = findViewById<Button>(R.id.btnSend)

        val rvChat = findViewById<RecyclerView>(R.id.rvChat)
        rvChat.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatMessageAdapter()
        rvChat.adapter = chatAdapter

        val rvEnvConfig = findViewById<RecyclerView>(R.id.rvEnvConfig)
        rvEnvConfig.layoutManager = LinearLayoutManager(this)
        envConfigAdapter = EnvConfigAdapter()
        rvEnvConfig.adapter = envConfigAdapter
        renderEnvConfigList()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { list ->
                    chatAdapter.submitList(list)
                    if (list.isNotEmpty()) {
                        rvChat.scrollToPosition(list.lastIndex)
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

        onWifiMaybeChanged()
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
            Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT).show()
            permissionHelper.requestWifiPermission()
            return
        }

        val ssid = wifiProvider.getCurrentWifiSsid()
        onWifiMaybeChanged()
        viewModel.handleUserInput(message, ssid)
        inputEditText.text?.clear()
        input = ""
    }

    private fun onWifiMaybeChanged() {
        if (!permissionHelper.hasWifiPermission()) {
            return
        }
        val ssid = wifiProvider.getCurrentWifiSsid() ?: return

        envState["Wi-Fi"] = ssid
        tvWifiStatus.text = getString(R.string.current_wifi_format, ssid)

        val result = ruleEngine.run(ssid)
        updateEnvFromRuleResult(result)
        viewModel.onRuleRunResult(result)
    }

    private fun updateEnvFromRuleResult(result: RuleRunResult) {
        when (result) {
            is RuleRunResult.Success -> {
                envState["规则状态"] = "执行成功"
                applyActionsToEnvState(result.action)
            }
            is RuleRunResult.NoRule -> {
                envState["规则状态"] = "未找到规则"
            }
            is RuleRunResult.SkippedDuplicate -> {
                envState["规则状态"] = "重复规则已跳过"
            }
            is RuleRunResult.Failed -> {
                envState["规则状态"] = "失败: ${result.reason}"
            }
        }
        renderEnvConfigList()
    }

    private fun applyActionsToEnvState(actions: List<Action>) {
        actions.forEach { action ->
            when (action.operation) {
                "set_brightness" -> envState["亮度"] = "${action.params["value"]}%"
                "set_volume" -> envState["音量"] = "${action.params["value"]}%"
                "open_bluetooth" -> envState["蓝牙"] = "开启"
                "close_bluetooth" -> envState["蓝牙"] = "关闭"
            }
        }
    }

    private fun renderEnvConfigList() {
        val list = envState.map { (key, value) -> EnvConfigItem(key, value) }
        envConfigAdapter.submitList(list)
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
            onWifiGranted = {
                onWifiMaybeChanged()
                trySendMessage()
            },
            onWifiDenied = {
                Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT).show()
            }
        )
    }
}
