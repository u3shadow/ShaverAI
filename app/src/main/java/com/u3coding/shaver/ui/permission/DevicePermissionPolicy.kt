package com.u3coding.shaver.ui.permission

import com.u3coding.shaver.action.model.Action

enum class DevicePermission {
    WRITE_SETTINGS,
    BLUETOOTH_CONNECT
}

/** Decide what to request before any action in a batch changes device state. */
object DevicePermissionPolicy {
    fun firstMissing(
        actions: List<Action>,
        canWriteSettings: Boolean,
        canConnectBluetooth: Boolean
    ): DevicePermission? = actions.firstNotNullOfOrNull { action ->
        when (action.operation) {
            "set_brightness" ->
                if (canWriteSettings) null else DevicePermission.WRITE_SETTINGS
            "open_bluetooth", "close_bluetooth" ->
                if (canConnectBluetooth) null else DevicePermission.BLUETOOTH_CONNECT
            else -> null
        }
    }
}
