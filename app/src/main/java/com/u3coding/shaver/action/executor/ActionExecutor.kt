package com.u3coding.shaver.action.executor

import android.content.Context
import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.device.BrightnessTool
import com.u3coding.shaver.device.ChangeBlueTooth
import com.u3coding.shaver.device.VolumeTool
import java.lang.Float

class ActionExecutor(
    context: Context
) {
    private val appContext = context.applicationContext

    fun execute(action: Action) {
        when (action.operation) {
            "set_volume" -> setVolume(action)
            "set_brightness" -> setBrightness(action)
            "open_bluetooth" -> openBluetooth()
            "close_bluetooth" -> closeBluetooth()
        }
    }

    private fun setVolume(action: Action) {
        val volumeTool = VolumeTool(appContext)
        volumeTool.setVolume(Float(action.params["value"].toString()).toInt())
    }

    private fun openBluetooth() {
        ChangeBlueTooth().open()
    }
    private fun closeBluetooth() {
        ChangeBlueTooth().close()
    }

    private fun setBrightness(action: Action) {
        val brightnessTool = BrightnessTool(appContext)
        brightnessTool.applyBrightness(Float(action.params["value"].toString()).toInt() )
    }
}
