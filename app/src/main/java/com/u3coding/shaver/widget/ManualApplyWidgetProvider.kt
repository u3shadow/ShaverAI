package com.u3coding.shaver.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.u3coding.shaver.R
import com.u3coding.shaver.ui.ManualApplyProxyActivity

class ManualApplyWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action != ACTION_WIDGET_APPLY) return

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val proxyIntent = Intent(context, ManualApplyProxyActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(proxyIntent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateSingleWidget(context, appWidgetManager, appWidgetId, STATE_NORMAL)
        }
    }

    companion object {
        private const val ACTION_WIDGET_APPLY = "com.u3coding.shaver.action.WIDGET_APPLY"
        private const val STATE_NORMAL = 0
        private const val STATE_LOADING = 1
        private const val STATE_SUCCESS = 2
        private const val STATE_FAIL = 3

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ManualApplyWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(componentName)
            widgetIds.forEach { widgetId ->
                updateSingleWidget(context, manager, widgetId, STATE_NORMAL)
            }
        }

        fun setWidgetLoading(context: Context, widgetId: Int) {
            setWidgetState(context, widgetId, STATE_LOADING)
        }

        fun setWidgetResult(context: Context, widgetId: Int, success: Boolean) {
            setWidgetState(context, widgetId, STATE_SUCCESS)
        }

        private fun updateSingleWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            state: Int
        ) {
            val remoteViews = RemoteViews(context.packageName, R.layout.widget_manual_apply)
            val intent = Intent(context, ManualApplyProxyActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                intent,
                pendingFlags()
            )
            remoteViews.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            applyState(remoteViews, state)
            manager.updateAppWidget(widgetId, remoteViews)
        }

        private fun setWidgetState(context: Context, widgetId: Int, state: Int) {
            val manager = AppWidgetManager.getInstance(context)
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                val componentName = ComponentName(context, ManualApplyWidgetProvider::class.java)
                manager.getAppWidgetIds(componentName).forEach { id ->
                    updateSingleWidget(context, manager, id, state)
                }
                return
            }
            updateSingleWidget(context, manager, widgetId, state)
        }

        private fun applyState(views: RemoteViews, state: Int) {
            if (state == STATE_LOADING) {
                views.setViewVisibility(R.id.widget_icon, View.GONE)
                views.setViewVisibility(R.id.widget_state_icon, View.GONE)
                views.setViewVisibility(R.id.widget_progress, View.VISIBLE)
                return
            }
            if (state == STATE_SUCCESS) {
                views.setViewVisibility(R.id.widget_icon, View.GONE)
                views.setViewVisibility(R.id.widget_progress, View.GONE)
                views.setViewVisibility(R.id.widget_state_icon, View.VISIBLE)
                views.setImageViewResource(
                    R.id.widget_state_icon,
                    R.drawable.ic_widget_success
                )
                return
            }
            views.setViewVisibility(R.id.widget_progress, View.GONE)
            views.setViewVisibility(R.id.widget_state_icon, View.GONE)
            views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
        }

        private fun pendingFlags(): Int {
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or PendingIntent.FLAG_IMMUTABLE
            }
            return flags
        }
    }
}
