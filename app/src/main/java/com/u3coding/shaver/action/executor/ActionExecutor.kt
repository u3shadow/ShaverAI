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

    fun execute(action: Action): Boolean {
        return when (action.operation) {
            "set_volume" -> {
                setVolume(action)
                true
            }
            "set_brightness" -> {
                setBrightness(action)
            }
            "open_bluetooth" -> openBluetooth()
            "close_bluetooth" -> closeBluetooth()
            else -> false
        }
    }

    private fun setVolume(action: Action) {
        val volumeTool = VolumeTool(appContext)
        volumeTool.setVolume(Float(action.params["value"].toString()).toInt())
    }

    private fun openBluetooth(): Boolean {
        return ChangeBlueTooth().open()
    }
    private fun closeBluetooth(): Boolean {
        return ChangeBlueTooth().close()
    }

    private fun setBrightness(action: Action): Boolean {
        val brightnessTool = BrightnessTool(appContext)
        return brightnessTool.applyBrightness(Float(action.params["value"].toString()).toInt())
    }
}
