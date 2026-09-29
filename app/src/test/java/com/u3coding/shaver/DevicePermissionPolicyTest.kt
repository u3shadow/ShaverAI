package com.u3coding.shaver

import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.ui.permission.DevicePermission
import com.u3coding.shaver.ui.permission.DevicePermissionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DevicePermissionPolicyTest {
    private val actions = listOf(
        Action("", "set_volume", mapOf("value" to 33)),
        Action("", "set_brightness", mapOf("value" to 100)),
        Action("", "open_bluetooth", emptyMap())
    )

    @Test
    fun requestsPermissionsBeforeExecutingBatch() {
        assertEquals(
            DevicePermission.WRITE_SETTINGS,
            DevicePermissionPolicy.firstMissing(actions, false, false)
        )
        assertEquals(
            DevicePermission.BLUETOOTH_CONNECT,
            DevicePermissionPolicy.firstMissing(actions, true, false)
        )
        assertNull(DevicePermissionPolicy.firstMissing(actions, true, true))
    }

    @Test
    fun volumeNeedsNoRuntimePermission() {
        assertNull(DevicePermissionPolicy.firstMissing(actions.take(1), false, false))
    }
}
