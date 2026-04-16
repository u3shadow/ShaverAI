package com.u3coding.shaver.action

import android.content.Context
import android.media.AudioManager
import com.u3coding.shaver.device.BrightnessTool
import com.u3coding.shaver.device.ChangeBlueTooth
import com.u3coding.shaver.device.VolumeTool

class ActionExecutor(
    context: Context
) {
    private val appContext = context.applicationContext

    fun execute(action: Action) {
        when (action.operation) {
            "set_volume" -> setVolume(action)
            "set_brightness" -> setBrightness(action)
            "open_bluetooth" -> openBluetooth()
            "close_bluetooth" -> openBluetooth()
        }
    }

    private fun setVolume(action: Action) {
        val volumeTool = VolumeTool(appContext)
        volumeTool.setVolume(action.params["value"] as? Int ?: return)
    }

    private fun openBluetooth() {
        ChangeBlueTooth().open()
    }

    private fun setBrightness(action: Action) {
        val brightnessTool = BrightnessTool(appContext)
        brightnessTool.applyBrightness(action.params["value"] as? Int ?: return)
    }
}