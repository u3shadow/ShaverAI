package com.u3coding.shaver.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.core.content.ContextCompat
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
import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.action.executor.ActionExecutor
import com.u3coding.shaver.action.rule.RuleEngine
import com.u3coding.shaver.action.rule.RuleEventType
import com.u3coding.shaver.action.rule.RuleRepo
import com.u3coding.shaver.action.rule.RuleRunResult
import com.u3coding.shaver.device.WifiSceneStateStore
import com.u3coding.shaver.device.WifiProvider
import com.u3coding.shaver.notification.ManualApplyNotification
import com.u3coding.shaver.widget.ManualApplyWidgetProvider
import com.u3coding.shaver.ui.adapter.ChatMessageAdapter
import com.u3coding.shaver.ui.adapter.EnvConfigAdapter
import com.u3coding.shaver.ui.chat.ChatUiEvent
import com.u3coding.shaver.ui.chat.ChatViewModel
import com.u3coding.shaver.ui.chat.ChatViewModelFactory
import com.u3coding.shaver.ui.model.EnvConfigItem
import com.u3coding.shaver.ui.permission.PermissionRequestHelper
import com.u3coding.shaver.ui.permission.DevicePermission
import com.u3coding.shaver.model.Role
import kotlinx.coroutines.launch
import kotlin.math.max

class MainActivity : AppCompatActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val writeSettingsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onDevicePermissionResult(
            DevicePermission.WRITE_SETTINGS,
            permissionHelper.hasWriteSettingsPermission()
        )
    }

    private lateinit var executor: ActionExecutor
    private lateinit var viewModel: ChatViewModel
    private lateinit var wifiProvider: WifiProvider
    private lateinit var wifiSceneStateStore: WifiSceneStateStore
    private lateinit var permissionHelper: PermissionRequestHelper
    private lateinit var inputEditText: EditText
    private lateinit var ruleEngine: RuleEngine

    private lateinit var tvWifiStatus: TextView
    private lateinit var tvEnvScrollHint: TextView
    private lateinit var pbEnvExecLoading: ProgressBar
    private lateinit var tvEnvExecResult: TextView
    private lateinit var topBar: View
    private lateinit var composerBar: View
    private lateinit var rvChat: RecyclerView
    private lateinit var rvEnvConfig: RecyclerView
    private lateinit var chatAdapter: ChatMessageAdapter
    private lateinit var envConfigAdapter: EnvConfigAdapter
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val wifiCheckHandler = Handler(Looper.getMainLooper())
    private val disconnectedWifiCheck = Runnable {
        if (wifiProvider.getCurrentWifiSsid().isNullOrBlank()) {
            runDisconnectedWifiScene()
        } else {
            onWifiMaybeChanged()
        }
    }

    private var input: String = ""
    private var lastHandledActionVersion: Long = -1L
    private var currentRuleSsid: String? = null
    private var lastReportedWifiSsid: String? = null
    private var wifiPermissionRequested = false
    private val changedConfigKeys = mutableSetOf<String>()
    private val envState = linkedMapOf(
        "Wi-Fi" to "\u672a\u77e5",
        KEY_CURRENT_BRIGHTNESS to "\u672a\u77e5",
        KEY_CURRENT_VOLUME to "\u672a\u77e5",
        KEY_CURRENT_BLUETOOTH to "\u672a\u77e5",
        KEY_DISCONNECT_BRIGHTNESS to "\u672a\u914d\u7f6e",
        KEY_DISCONNECT_VOLUME to "\u672a\u914d\u7f6e",
        KEY_DISCONNECT_BLUETOOTH to "\u672a\u914d\u7f6e"
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
        val factory = ChatViewModelFactory(executor,applicationContext)
        viewModel = ViewModelProvider(this, factory)[ChatViewModel::class.java]
        wifiProvider = WifiProvider(this)
        wifiSceneStateStore = WifiSceneStateStore(this)
        permissionHelper = PermissionRequestHelper(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        requestNotificationPermissionIfNeeded()
        ManualApplyWidgetProvider.refreshAll(this)
        showOnboardingIfNeeded()

        tvWifiStatus = findViewById(R.id.tvWifiStatus)
        tvEnvScrollHint = findViewById(R.id.tvEnvScrollHint)
        pbEnvExecLoading = findViewById(R.id.pbEnvExecLoading)
        tvEnvExecResult = findViewById(R.id.tvEnvExecResult)
        topBar = findViewById(R.id.topBar)
        composerBar = findViewById(R.id.composerBar)
        val et = findViewById<EditText>(R.id.etInput)
        inputEditText = et
        val btn = findViewById<Button>(R.id.btnSend)
        findViewById<Button>(R.id.btnOpenGuide).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
        findViewById<Button>(R.id.btnManageScenes).setOnClickListener {
            startActivity(Intent(this, SceneListActivity::class.java))
        }

        rvChat = findViewById(R.id.rvChat)
        rvChat.layoutManager = LinearLayoutManager(this)
        chatAdapter = ChatMessageAdapter()
        rvChat.adapter = chatAdapter

        rvEnvConfig = findViewById(R.id.rvEnvConfig)
        rvEnvConfig.layoutManager = LinearLayoutManager(this)
        envConfigAdapter = EnvConfigAdapter()
        rvEnvConfig.adapter = envConfigAdapter
        rvEnvConfig.setOnScrollChangeListener { _, _, _, _, _ ->
            updateEnvScrollHint()
        }

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
                        notifyManualApplyResult(true, actions)
                        refreshChatList()
                        setExecutionSuccess()
                    }
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is ChatUiEvent.RequestDevicePermission -> {
                                requestDevicePermission(event.permission)
                            }
                            is ChatUiEvent.ShowToast -> {
                                Toast.makeText(this@MainActivity, event.message, Toast.LENGTH_SHORT).show()
                            }
                            is ChatUiEvent.ShowSnackbar -> {
                                val root = findViewById<View>(R.id.rootContainer)
                                Snackbar.make(root, event.message, Snackbar.LENGTH_SHORT).show()
                            }
                            is ChatUiEvent.ConfirmRule -> {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("确认保存规则")
                                    .setMessage("是否保存规则“${event.name}”？")
                                    .setPositiveButton("保存") { _, _ -> viewModel.confirmPendingRule() }
                                    .setNegativeButton("取消") { _, _ -> viewModel.discardPendingRule() }
                                    .show()
                            }
                            is ChatUiEvent.UseLocalConnectedRule -> {
                                val result = ruleEngine.run(event.ssid, RuleEventType.WIFI_CONNECTED)
                                updateEnvFromRuleResult(result)
                                renderEnvConfigList()
                                viewModel.onRuleRunResult(result)
                            }
                            is ChatUiEvent.RequestFinished -> {
                                if (event.success) setExecutionSuccess() else setExecutionFailed()
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


    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestDevicePermission(permission: DevicePermission) {
        when (permission) {
            DevicePermission.WRITE_SETTINGS -> {
                if (permissionHelper.hasWriteSettingsPermission()) {
                    viewModel.onDevicePermissionResult(permission, true)
                    return
                }
                AlertDialog.Builder(this)
                    .setTitle("允许修改系统设置")
                    .setMessage("调整屏幕亮度需要此特殊权限。打开系统设置并允许 ShaverAI 修改系统设置，返回后会继续执行当前计划。")
                    .setPositiveButton("打开设置") { _, _ ->
                        try {
                            writeSettingsPermissionLauncher.launch(
                                permissionHelper.writeSettingsPermissionIntent()
                            )
                        } catch (_: ActivityNotFoundException) {
                            viewModel.onDevicePermissionResult(permission, false)
                        }
                    }
                    .setNegativeButton("取消") { _, _ ->
                        viewModel.onDevicePermissionResult(permission, false)
                    }
                    .setOnCancelListener {
                        viewModel.onDevicePermissionResult(permission, false)
                    }
                    .show()
            }
            DevicePermission.BLUETOOTH_CONNECT -> {
                if (permissionHelper.hasBluetoothConnectPermission()) {
                    viewModel.onDevicePermissionResult(permission, true)
                } else {
                    permissionHelper.requestBluetoothConnectPermission()
                }
            }
        }
    }

    private fun showOnboardingIfNeeded() {
        if (OnboardingActivity.shouldShow(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
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
        if (permissionHelper.hasWifiPermission()) {
            onWifiMaybeChanged()
        } else if (!wifiPermissionRequested) {
            wifiPermissionRequested = true
            permissionHelper.requestWifiPermission()
        }
    }

    override fun onStart() {
        super.onStart()
        registerWifiStateCallback()
    }

    override fun onStop() {
        wifiCheckHandler.removeCallbacks(disconnectedWifiCheck)
        unregisterWifiStateCallback()
        super.onStop()
    }

    private fun trySendMessage() {
        val message = input.trim()
        if (message.isBlank()) {
            return
        }
        setExecutionRunning()
        val canSend = viewModel.canSendMessage(message)
        if (!canSend) {
            setExecutionFailed()
            return
        }

        val ssid = if (permissionHelper.hasWifiPermission()) {
            wifiProvider.getCurrentWifiSsid()
        } else {
            null
        }
        viewModel.handleUserInput(message, ssid)
        inputEditText.text?.clear()
        input = ""
    }

    private fun onWifiMaybeChanged() {
        if (!permissionHelper.hasWifiPermission()) {
            return
        }

        val ssid = wifiProvider.getCurrentWifiSsid()
        if (ssid.isNullOrBlank()) {
            scheduleDisconnectedWifiCheck()
            return
        }

        wifiCheckHandler.removeCallbacks(disconnectedWifiCheck)
        currentRuleSsid = ssid
        wifiSceneStateStore.saveLastConnectedSsid(ssid)
        updateDisplayedWifiSsid(ssid)
        loadDisconnectPlanForSsid(ssid)

        if (lastReportedWifiSsid != ssid) {
            lastReportedWifiSsid = ssid
            setExecutionRunning()
            viewModel.onSystemEvent(
                event = "wifi_changed",
                wifiSsid = ssid
            )
        }
    }

    private fun scheduleDisconnectedWifiCheck() {
        wifiCheckHandler.removeCallbacks(disconnectedWifiCheck)
        wifiCheckHandler.postDelayed(disconnectedWifiCheck, DISCONNECT_CONFIRM_DELAY_MS)
    }

    private fun runDisconnectedWifiScene() {
        lastReportedWifiSsid = null
        val lastSsid = wifiSceneStateStore.getLastConnectedSsid()
        if (lastSsid.isNullOrBlank()) {
            updateDisplayedWifiSsid("\u672a\u8fde\u63a5")
            changedConfigKeys.clear()
            renderEnvConfigList()
            setExecutionFailed()
            notifyManualApplyResult(false)
            return
        }

        currentRuleSsid = lastSsid
        updateDisplayedWifiSsid("$lastSsid (disconnected)")
        loadDisconnectPlanForSsid(lastSsid)
        viewModel.onSystemEvent(
            event = "wifi_disconnected",
            wifiSsid = lastSsid
        )
        val result = ruleEngine.run(lastSsid, RuleEventType.WIFI_DISCONNECTED)
        updateEnvFromRuleResult(result)
        renderEnvConfigList()
        viewModel.onRuleRunResult(result)
    }

    private fun updateDisplayedWifiSsid(ssid: String) {
        envState["Wi-Fi"] = ssid
        tvWifiStatus.text = getString(R.string.current_wifi_format, ssid)
        renderEnvConfigList()
    }


    private fun notifyManualApplyResult(applied: Boolean, actions: List<Action> = emptyList()) {
        val firstAction = actions.firstOrNull()
        ManualApplyNotification.showResult(
            context = this,
            applied = applied,
            actions = actions,
            sceneSsid = firstAction?.trigger ?: currentRuleSsid,
            eventType = firstAction?.eventType ?: RuleEventType.WIFI_CONNECTED
        )
        ManualApplyWidgetProvider.refreshAll(this)
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
                notifyManualApplyResult(false)
            }
            is RuleRunResult.SkippedDuplicate -> {
                changedConfigKeys.clear()
                setExecutionFailed()
                notifyManualApplyResult(false)
            }
            is RuleRunResult.Failed -> {
                changedConfigKeys.clear()
                setExecutionFailed()
                notifyManualApplyResult(false)
                Snackbar.make(
                    findViewById(R.id.rootContainer),
                    result.reason,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun applyActionsToEnvState(actions: List<Action>) {
        changedConfigKeys.clear()
        actions.forEach { action ->
            if (RuleEventType.normalize(action.eventType) == RuleEventType.WIFI_DISCONNECTED) {
                applyDisconnectPlanAction(action, highlighted = true)
                return@forEach
            }
            when (action.operation) {
                "set_brightness" -> {
                    envState[KEY_CURRENT_BRIGHTNESS] = "${action.params["value"]}%"
                    changedConfigKeys.add(KEY_CURRENT_BRIGHTNESS)
                }
                "set_volume" -> {
                    envState[KEY_CURRENT_VOLUME] = "${action.params["value"]}%"
                    changedConfigKeys.add(KEY_CURRENT_VOLUME)
                }
                "open_bluetooth" -> {
                    envState[KEY_CURRENT_BLUETOOTH] = "\u5f00"
                    changedConfigKeys.add(KEY_CURRENT_BLUETOOTH)
                }
                "close_bluetooth" -> {
                    envState[KEY_CURRENT_BLUETOOTH] = "\u5173"
                    changedConfigKeys.add(KEY_CURRENT_BLUETOOTH)
                }
            }
        }
    }

    private fun loadDisconnectPlanForSsid(ssid: String) {
        envState[KEY_DISCONNECT_BRIGHTNESS] = "\u672a\u914d\u7f6e"
        envState[KEY_DISCONNECT_VOLUME] = "\u672a\u914d\u7f6e"
        envState[KEY_DISCONNECT_BLUETOOTH] = "\u672a\u914d\u7f6e"
        RuleRepo.getRules(ssid, RuleEventType.WIFI_DISCONNECTED).forEach { action ->
            applyDisconnectPlanAction(action, highlighted = false)
        }
    }

    private fun applyDisconnectPlanAction(action: Action, highlighted: Boolean) {
        when (action.operation) {
            "set_brightness" -> {
                envState[KEY_DISCONNECT_BRIGHTNESS] = "${action.params["value"]}%"
                if (highlighted) changedConfigKeys.add(KEY_DISCONNECT_BRIGHTNESS)
            }
            "set_volume" -> {
                envState[KEY_DISCONNECT_VOLUME] = "${action.params["value"]}%"
                if (highlighted) changedConfigKeys.add(KEY_DISCONNECT_VOLUME)
            }
            "open_bluetooth" -> {
                envState[KEY_DISCONNECT_BLUETOOTH] = "\u5f00"
                if (highlighted) changedConfigKeys.add(KEY_DISCONNECT_BLUETOOTH)
            }
            "close_bluetooth" -> {
                envState[KEY_DISCONNECT_BLUETOOTH] = "\u5173"
                if (highlighted) changedConfigKeys.add(KEY_DISCONNECT_BLUETOOTH)
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
        rvEnvConfig.post { updateEnvScrollHint() }
    }

    private fun updateEnvScrollHint() {
        tvEnvScrollHint.visibility = if (rvEnvConfig.canScrollVertically(1)) {
            View.VISIBLE
        } else {
            View.GONE
        }
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
        showExecutionResult("\u2713", "#62F59A")
    }

    private fun setExecutionFailed() {
        showExecutionResult("\u00d7", "#FF5D5D")
    }

    private fun showExecutionResult(symbol: String, colorHex: String) {
        pbEnvExecLoading.visibility = View.GONE
        tvEnvExecResult.text = symbol
        tvEnvExecResult.setTextColor(Color.parseColor(colorHex))
        tvEnvExecResult.visibility = View.VISIBLE
        tvEnvExecResult.alpha = 0f
        tvEnvExecResult.animate().alpha(1f).setDuration(220).start()
    }

    private fun registerWifiStateCallback() {
        if (networkCallback != null) return
        val manager = connectivityManager ?: return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { onWifiMaybeChanged() }
            }

            override fun onLost(network: Network) {
                runOnUiThread { onWifiMaybeChanged() }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    runOnUiThread { onWifiMaybeChanged() }
                }
            }
        }
        try {
            manager.registerDefaultNetworkCallback(networkCallback!!)
        } catch (_: Exception) {
            networkCallback = null
        }
    }

    private fun unregisterWifiStateCallback() {
        val callback = networkCallback ?: return
        val manager = connectivityManager ?: return
        try {
            manager.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    companion object {
        private const val DISCONNECT_CONFIRM_DELAY_MS = 1500L
        private const val KEY_CURRENT_BRIGHTNESS = "\u5f53\u524d\u4eae\u5ea6"
        private const val KEY_CURRENT_VOLUME = "\u5f53\u524d\u97f3\u91cf"
        private const val KEY_CURRENT_BLUETOOTH = "\u5f53\u524d\u84dd\u7259"
        private const val KEY_DISCONNECT_BRIGHTNESS = "\u65ad\u5f00\u540e\u4eae\u5ea6"
        private const val KEY_DISCONNECT_VOLUME = "\u65ad\u5f00\u540e\u97f3\u91cf"
        private const val KEY_DISCONNECT_BLUETOOTH = "\u65ad\u5f00\u540e\u84dd\u7259"
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
            onBluetoothGranted = {
                viewModel.onDevicePermissionResult(DevicePermission.BLUETOOTH_CONNECT, true)
            },
            onBluetoothDenied = {
                viewModel.onDevicePermissionResult(DevicePermission.BLUETOOTH_CONNECT, false)
            },
            onWifiGranted = {
                onWifiMaybeChanged()
                trySendMessage()
            },
            onWifiDenied = {
                Toast.makeText(this, getString(R.string.wifi_permission_denied), Toast.LENGTH_SHORT).show()
                setExecutionFailed()
                notifyManualApplyResult(false)
            }
        )
    }
}
