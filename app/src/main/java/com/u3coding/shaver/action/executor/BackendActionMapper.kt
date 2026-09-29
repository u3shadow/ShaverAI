package com.u3coding.shaver.action.executor

import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.data.remote.BackendActionItem
import kotlin.math.roundToInt

/** Maps the backend's validated plan into this app's device action format. */
object BackendActionMapper {
    fun toLocalAction(item: BackendActionItem, wifiSsid: String?): Action {
        val trigger = wifiSsid.orEmpty()
        return when (item.action) {
            "set_volume" -> {
                val level = integer(item.params["level"], 0, 15, "level")
                Action(trigger, "set_volume", mapOf("value" to (level * 100f / 15f).roundToInt()))
            }
            "set_brightness" -> {
                val value = integer(item.params["value"], 0, 100, "value")
                Action(trigger, "set_brightness", mapOf("value" to value))
            }
            "set_bluetooth" -> {
                val enabled = item.params["enabled"] as? Boolean
                    ?: throw IllegalArgumentException("enabled 必须是布尔值")
                Action(trigger, if (enabled) "open_bluetooth" else "close_bluetooth", emptyMap())
            }
            else -> throw IllegalArgumentException("不支持的设备动作：${item.action}")
        }
    }

    private fun integer(value: Any?, min: Int, max: Int, name: String): Int {
        val number = value as? Number ?: throw IllegalArgumentException("$name 必须是整数")
        val decimal = number.toDouble()
        require(decimal.isFinite() && decimal % 1.0 == 0.0 && decimal in min.toDouble()..max.toDouble()) {
            "$name 必须是 $min 到 $max 之间的整数"
        }
        return decimal.toInt()
    }
}
