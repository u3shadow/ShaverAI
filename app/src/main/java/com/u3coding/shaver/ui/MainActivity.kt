package com.u3coding.shaver.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
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
import com.u3coding.shaver.ui.chat.ChatUiEvent
import com.u3coding.shaver.ui.chat.ChatViewModel
import com.u3coding.shaver.ui.chat.ChatViewModelFactory
import com.u3coding.shaver.ui.model.EnvConfigItem
import com.u3coding.shaver.ui.permission.PermissionRequestHelper
import com.u3coding.shaver.model.Role
import kotlinx.coroutines.launch
import kotlin.math.max

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
    private lateinit var topBar: View
    private lateinit var composerBar: View
    private lateinit var rvChat: RecyclerView
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var envConfigAdapter: EnvConfigAdapter

    private var input: String = ""
    private var lastHandledActionVersion: Long = -1L
    private val changedConfigKeys = mutableSetOf<String>()
    private val envState = linkedMapOf(
        "Wi-Fi" to "unknown",
        "亮度" to "unknown",
        "音量" to "unknown",
        "蓝牙" to "unknown"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
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
        topBar = findViewById(R.id.topBar)
        composerBar = findViewById(R.id.composerBar)
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

        renderEnvConfigList()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        val visibleList = state.messages.filter { it.role != Role.SYSTEM }
                        chatAdapter.submitList(visibleList)
                        if (visibleList.isNotEmpty()) {
                            rvChat.scrollToPosition(visibleList.lastIndex)
                        }

                    val actions = state.lastSuccessfulActions
                    if (actions.isNotEmpty() && state.actionUpdateVersion != lastHandledActionVersion) {
                        lastHandledActionVersion = state.actionUpdateVersion
                        applyActionsToEnvState(actions)
                        renderEnvConfigList()
                        refreshChatList()
                        setExecutionSuccess()
                    }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ChatUiEvent.RequestBluetoothPermission -> {
                                permissionHelper.requestBluetoothConnectPermission()
                            }
                            is ChatUiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is ChatUiEvent.ShowSnackbar -> {
                                val root = findViewById<View>(R.id.rootContainer)
                                Snackbar.make(root, event.message, Snackbar.LENGTH_SHORT).show()
                            }
                            is ChatUiEvent.Navigate -> {
                                // reserved for future navigation events
                            }
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

        setupInsets()
        onWifiMaybeChanged()
    }

    private fun setupInsets() {
        val root = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val statusInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            topBar.updatePadding(top = statusInsets.top + dp(14))

            val bottomInset = max(navInsets.bottom, imeInsets.bottom)
            composerBar.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = dp(12) + bottomInset
            }

            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()
        onWifiMaybeChanged()
    }

    private fun trySendMessage() {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }
        setExecutionRunning()
        val canSend = viewModel.canSendMessage(
            input = message,
            hasBluetoothPermission = permissionHelper.hasBluetoothConnectPermission(),
            hasWriteSettingsPermission = permissionHelper.hasWriteSettingsPermission(),
            hasWifiPermission = permissionHelper.hasWifiPermission()
        )
        if (!canSend) {
            if (!permissionHelper.hasWifiPermission()) {
                permissionHelper.requestWifiPermission()
            }
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

        viewModel.onSystemEvent(
            event = "wifi_changed",
            wifiSsid = ssid
        )
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
        val list = viewModel.uiState.value.messages.filter { it.role != Role.SYSTEM }
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
