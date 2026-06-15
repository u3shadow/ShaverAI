package com.u3coding.shaver.action.rule

object RuleEventType {
    const val WIFI_CONNECTED = "wifi_connected"
    const val WIFI_DISCONNECTED = "wifi_disconnected"

    fun normalize(value: String?): String {
        return when (value?.trim()?.lowercase()) {
            WIFI_DISCONNECTED,
            "disconnect",
            "disconnected",
            "wifi_disconnect",
            "leave_wifi",
            "on_disconnect" -> WIFI_DISCONNECTED
            else -> WIFI_CONNECTED
        }
    }
}
