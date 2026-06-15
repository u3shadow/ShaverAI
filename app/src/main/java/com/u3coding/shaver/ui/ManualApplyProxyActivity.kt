package com.u3coding.shaver.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.u3coding.shaver.action.executor.ActionExecutor
import com.u3coding.shaver.action.rule.RuleEventType
import com.u3coding.shaver.action.rule.RuleRepo
import com.u3coding.shaver.device.WifiProvider
import com.u3coding.shaver.device.WifiSceneStateStore
import com.u3coding.shaver.notification.ManualApplyNotification
import com.u3coding.shaver.widget.ManualApplyWidgetProvider

class ManualApplyProxyActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyInvisibleWindow()
        runApplyThenFinish()
    }

    private fun applyInvisibleWindow() {
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window?.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window?.statusBarColor = Color.TRANSPARENT
            window?.navigationBarColor = Color.TRANSPARENT
            window?.decorView?.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }

    private fun runApplyThenFinish() {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val appContext = applicationContext
        Thread {
            try {
                ManualApplyWidgetProvider.setWidgetLoading(appContext, widgetId)
                RuleRepo.init(appContext)
                val ssid = WifiProvider(appContext).getCurrentWifiSsid()
                val stateStore = WifiSceneStateStore(appContext)
                val scene = resolveScene(ssid, stateStore)
                if (scene == null) {
                    ManualApplyNotification.showResult(appContext, applied = false)
                    ManualApplyWidgetProvider.setWidgetResult(appContext, widgetId, success = false)
                } else {
                    val originalActions = RuleRepo.getRules(scene.ssid, scene.eventType)
                    val executableActions = originalActions.filterNot { it.isBluetoothAction() }
                    val applied = originalActions.isNotEmpty()
                    if (applied) {
                        val executor = ActionExecutor(appContext)
                        executableActions.forEach { action ->
                            executor.execute(action)
                        }
                    }
                    ManualApplyNotification.showResult(
                        context = appContext,
                        applied = applied,
                        actions = executableActions,
                        sceneSsid = scene.ssid,
                        eventType = scene.eventType
                    )
                    ManualApplyWidgetProvider.setWidgetResult(appContext, widgetId, applied)
                }
                sleepQuietly(RESULT_HOLD_MS)
                ManualApplyWidgetProvider.refreshAll(appContext)
            } finally {
                runOnUiThread {
                    finish()
                    overridePendingTransition(0, 0)
                }
            }
        }.apply { name = "manual-apply-proxy" }.start()
    }

    private fun resolveScene(ssid: String?, stateStore: WifiSceneStateStore): ApplyScene? {
        return if (ssid.isNullOrBlank()) {
            stateStore.getLastConnectedSsid()?.let {
                ApplyScene(it, RuleEventType.WIFI_DISCONNECTED)
            }
        } else {
            stateStore.saveLastConnectedSsid(ssid)
            ApplyScene(ssid, RuleEventType.WIFI_CONNECTED)
        }
    }

    private fun com.u3coding.shaver.action.model.Action.isBluetoothAction(): Boolean {
        return operation == "open_bluetooth" || operation == "close_bluetooth"
    }

    private fun sleepQuietly(durationMs: Long) {
        try {
            Thread.sleep(durationMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val RESULT_HOLD_MS = 1000L
    }
}

private data class ApplyScene(
    val ssid: String,
    val eventType: String
)
