package com.u3coding.shaver.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
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
import com.u3coding.shaver.action.RuleRepo
import com.u3coding.shaver.action.RuleRunResult
import com.u3coding.shaver.device.WifiProvider
import com.u3coding.shaver.ui.adapter.ChatMessageAdapter
import com.u3coding.shaver.ui.adapter.EnvConfigAdapter
import com.u3coding.shaver.ui.adapter.RuleListAdapter
import com.u3coding.shaver.ui.chat.ChatViewModel
import com.u3coding.shaver.ui.chat.ChatViewModelFactory
import com.u3coding.shaver.ui.model.EnvConfigItem
import com.u3coding.shaver.ui.model.RuleDisplayItem
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
    private lateinit var pbEnvExecLoading: ProgressBar
    private lateinit var tvEnvExecResult: TextView
    private lateinit var rvChat: RecyclerView
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var envConfigAdapter: EnvConfigAdapter
    private lateinit var spRuleProfiles: Spinner
    private lateinit var ruleListAdapter: RuleListAdapter

    private var input: String = ""
    private var currentSelectedTrigger: String? = null
    private val changedConfigKeys = mutableSetOf<String>()
    private val envState = linkedMapOf(
        "Wi-Fi" to "unknown",
        "亮度" to "unknown",
        "音量" to "unknown",
        "蓝牙" to "unknown"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        RuleRepo.init(applicationContext)
        ruleEngine = RuleEngine(this)
        executor = ActionExecutor(applicationContext)
        val factory = ChatViewModelFactory(executor)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        wifiProvider = WifiProvider(this)
        permissionHelper = PermissionRequestHelper(this)

        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        pbEnvExecLoading = findViewById(R.id.pbEnvExecLoading)
        tvEnvExecResult = findViewById(R.id.tvEnvExecResult)
        val et = findViewById<EditText>(R.id.etInput)
        inputEditText = et
        val btn = findViewById<Button>(R.id.btnSend)

        rvChat = findViewById(R.id.rvChat)
        rvChat.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatMessageAdapter()
        rvChat.adapter = chatAdapter

        val rvEnvConfig = findViewById<RecyclerView>(R.id.rvEnvConfig)
        rvEnvConfig.layoutManager = LinearLayoutManager(this)
        envConfigAdapter = EnvConfigAdapter()
        rvEnvConfig.adapter = envConfigAdapter

        spRuleProfiles = findViewById(R.id.spRuleProfiles)
        val rvRuleList = findViewById<RecyclerView>(R.id.rvRuleList)
        rvRuleList.layoutManager = LinearLayoutManager(this)
        ruleListAdapter = RuleListAdapter()
        rvRuleList.adapter = ruleListAdapter
        setupRuleProfileSpinner()

        renderEnvConfigList()
        reloadRuleProfiles()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.messages.collect { list ->
                        chatAdapter.submitList(list)
                        if (list.isNotEmpty()) {
                            rvChat.scrollToPosition(list.lastIndex)
                        }
                    }
                }
                launch {
                    viewModel.lastSuccessfulActions.collect { actions ->
                        if (actions.isNotEmpty()) {
                            applyActionsToEnvState(actions)
                            renderEnvConfigList()
                            refreshChatList()
                            setExecutionSuccess()
                            reloadRuleProfiles(actions.firstOrNull()?.trigger)
                        }
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

    override fun onResume() {
        super.onResume()
        onWifiMaybeChanged()
    }

    private fun setupRuleProfileSpinner() {
        spRuleProfiles.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position)?.toString().orEmpty()
                if (selected.isBlank() || selected == "暂无配置") {
                    ruleListAdapter.submitList(emptyList())
                    currentSelectedTrigger = null
                    return
                }
                currentSelectedTrigger = selected
                updateDisplayedWifiSsid(selected)
                renderRuleListForTrigger(selected)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }
    }

    private fun reloadRuleProfiles(preferredTrigger: String? = null) {
        val triggers = RuleRepo.getAllTriggers()
        val spinnerItems = if (triggers.isEmpty()) listOf("暂无配置") else triggers
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerItems)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spRuleProfiles.adapter = adapter

        if (triggers.isEmpty()) {
            currentSelectedTrigger = null
            ruleListAdapter.submitList(emptyList())
            return
        }

        val target = when {
            !preferredTrigger.isNullOrBlank() && triggers.contains(preferredTrigger) -> preferredTrigger
            !currentSelectedTrigger.isNullOrBlank() && triggers.contains(currentSelectedTrigger) -> currentSelectedTrigger
            else -> triggers.first()
        } ?: triggers.first()

        currentSelectedTrigger = target
        spRuleProfiles.setSelection(triggers.indexOf(target), false)
        updateDisplayedWifiSsid(target)
        renderRuleListForTrigger(target)
    }

    private fun renderRuleListForTrigger(trigger: String) {
        val actions = RuleRepo.getRules(trigger)
        val items = actions.map {
            RuleDisplayItem(
                operation = operationLabel(it.operation),
                value = actionValueLabel(it)
            )
        }
        ruleListAdapter.submitList(items)
    }

    private fun operationLabel(operation: String): String {
        return when (operation) {
            "set_brightness" -> "亮度"
            "set_volume" -> "音量"
            "open_bluetooth" -> "蓝牙"
            "close_bluetooth" -> "蓝牙"
            else -> operation
        }
    }

    private fun actionValueLabel(action: Action): String {
        return when (action.operation) {
            "set_brightness", "set_volume" -> "${action.params["value"] ?: "-"}%"
            "open_bluetooth" -> "开启"
            "close_bluetooth" -> "关闭"
            else -> action.params.toString()
        }
    }

    private fun trySendMessage() {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }
        setExecutionRunning()

        if (viewModel.requiresBluetoothPermission(message) && !permissionHelper.hasBluetoothConnectPermission()) {
            permissionHelper.requestBluetoothConnectPermission()
            return
        }
        if (!permissionHelper.hasWriteSettingsPermission()) {
            Toast.makeText(this, getString(R.string.write_settings_not_granted), Toast.LENGTH_SHORT).show()
            setExecutionFailed()
            return
        }
        if (!permissionHelper.hasWifiPermission()) {
            Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT).show()
            permissionHelper.requestWifiPermission()
            setExecutionFailed()
            return
        }

        val ssid = wifiProvider.getCurrentWifiSsid()
        viewModel.handleUserInput(message, ssid)
        inputEditText.text?.clear()
        input = ""
    }

    private fun onWifiMaybeChanged() {
        if (!permissionHelper.hasWifiPermission()) {
            return
        }
        setExecutionRunning()
        val ssid = wifiProvider.getCurrentWifiSsid() ?: return

        updateDisplayedWifiSsid(ssid)
        reloadRuleProfiles(ssid)

        val result = ruleEngine.run(ssid)
        updateEnvFromRuleResult(result)
        renderEnvConfigList()
        viewModel.onRuleRunResult(result)
    }

    private fun updateDisplayedWifiSsid(ssid: String) {
        envState["Wi-Fi"] = ssid
        tvWifiStatus.text = getString(R.string.current_wifi_format, ssid)
        renderEnvConfigList()
    }

    private fun updateEnvFromRuleResult(result: RuleRunResult) {
        when (result) {
            is RuleRunResult.Success -> {
                viewModel.onActionsExecuted(result.action)
                setExecutionSuccess()
            }
            is RuleRunResult.NoRule -> {
                changedConfigKeys.clear()
                setExecutionFailed()
            }
            is RuleRunResult.SkippedDuplicate -> {
                changedConfigKeys.clear()
                setExecutionFailed()
            }
            is RuleRunResult.Failed -> {
                changedConfigKeys.clear()
                setExecutionFailed()
            }
        }
    }

    private fun applyActionsToEnvState(actions: List<Action>) {
        changedConfigKeys.clear()
        actions.forEach { action ->
            when (action.operation) {
                "set_brightness" -> {
                    envState["亮度"] = "${action.params["value"]}%"
                    changedConfigKeys.add("亮度")
                }
                "set_volume" -> {
                    envState["音量"] = "${action.params["value"]}%"
                    changedConfigKeys.add("音量")
                }
                "open_bluetooth" -> {
                    envState["蓝牙"] = "开启"
                    changedConfigKeys.add("蓝牙")
                }
                "close_bluetooth" -> {
                    envState["蓝牙"] = "关闭"
                    changedConfigKeys.add("蓝牙")
                }
            }
        }
    }

    private fun renderEnvConfigList() {
        val list = envState.map { (key, value) ->
            EnvConfigItem(
                key = key,
                value = value,
                highlighted = changedConfigKeys.contains(key)
            )
        }
        envConfigAdapter.submitList(list)
    }

    private fun refreshChatList() {
        val list = viewModel.messages.value
        chatAdapter.submitList(list.toList())
        if (list.isNotEmpty()) {
            rvChat.scrollToPosition(list.lastIndex)
        }
    }

    private fun setExecutionRunning() {
        pbEnvExecLoading.visibility = View.VISIBLE
        tvEnvExecResult.visibility = View.GONE
    }

    private fun setExecutionSuccess() {
        showExecutionResult("✓", "#62F59A")
    }

    private fun setExecutionFailed() {
        showExecutionResult("✕", "#FF5D5D")
    }

    private fun showExecutionResult(symbol: String, colorHex: String) {
        pbEnvExecLoading.visibility = View.GONE
        tvEnvExecResult.text = symbol
        tvEnvExecResult.setTextColor(Color.parseColor(colorHex))
        tvEnvExecResult.visibility = View.VISIBLE
        tvEnvExecResult.alpha = 0f
        tvEnvExecResult.animate().alpha(1f).setDuration(220).start()
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
                setExecutionFailed()
            }
        )
    }
}
