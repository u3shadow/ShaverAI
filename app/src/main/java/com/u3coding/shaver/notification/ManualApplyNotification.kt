package com.u3coding.shaver.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.u3coding.shaver.R
import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.action.rule.RuleEventType
import com.u3coding.shaver.ui.MainActivity

object ManualApplyNotification {
    private const val CHANNEL_ID = "shaver_manual_apply"
    private const val NOTIFY_ID = 2201

    fun showResult(
        context: Context,
        applied: Boolean,
        actions: List<Action> = emptyList(),
        sceneSsid: String? = null,
        eventType: String = RuleEventType.WIFI_CONNECTED
    ) {
        val appContext = context.applicationContext
        createChannelIfNeeded(appContext)
        if (!canPostNotifications(appContext)) return

        val openIntent = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            101,
            openIntent,
            pendingFlags()
        )

        val sceneText = buildSceneText(applied, sceneSsid, eventType)
        val detailText = buildDetailText(applied, actions)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_wifi_scene)
            .setContentTitle(sceneText)
            .setContentText(detailText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFY_ID, notification)
    }

    private fun buildSceneText(applied: Boolean, sceneSsid: String?, eventType: String): String {
        if (!applied) return "\u672a\u5339\u914d\u5230\u914d\u7f6e"
        val ssid = sceneSsid?.takeIf { it.isNotBlank() } ?: "\u672a\u77e5\u65e0\u7ebf\u7f51"
        val actionText = if (RuleEventType.normalize(eventType) == RuleEventType.WIFI_DISCONNECTED) {
            "\u79bb\u5f00"
        } else {
            "\u8fdb\u5165"
        }
        return "\u5f53\u524d\u573a\u666f\uff1a$actionText $ssid"
    }

    private fun buildDetailText(applied: Boolean, actions: List<Action>): String {
        if (!applied) return "\u8bf7\u5148\u4e3a\u5f53\u524d\u65e0\u7ebf\u7f51\u914d\u7f6e\u89c4\u5219"
        val summary = actions
            .filterNot { it.isBluetoothAction() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = " | ") { it.toSummaryText() }
            ?: "\u65e0\u53ef\u81ea\u52a8\u5e94\u7528\u914d\u7f6e"
        return "\u8be6\u7ec6\u914d\u7f6e\uff1a$summary"
    }

    private fun Action.toSummaryText(): String {
        return when (operation) {
            "set_brightness" -> "\u4eae\u5ea6 ${params["value"] ?: "?"}%"
            "set_volume" -> "\u97f3\u91cf ${params["value"] ?: "?"}%"
            else -> operation
        }
    }

    private fun Action.isBluetoothAction(): Boolean {
        return operation == "open_bluetooth" || operation == "close_bluetooth"
    }

    private fun canPostNotifications(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }

    private fun createChannelIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "\u573a\u666f\u52a9\u624b\u5feb\u901f\u5e94\u7528",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "\u4e00\u952e\u68c0\u6d4b\u65e0\u7ebf\u7f51\u5e76\u5e94\u7528\u573a\u666f\u914d\u7f6e"
        }
        manager.createNotificationChannel(channel)
    }
}
