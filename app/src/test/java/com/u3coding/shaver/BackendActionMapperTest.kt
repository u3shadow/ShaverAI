package com.u3coding.shaver

import com.u3coding.shaver.action.executor.BackendActionMapper
import com.u3coding.shaver.data.remote.BackendActionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackendActionMapperTest {
    @Test
    fun volumeLevelUsesBackendRangeAndLocalPercent() {
        val action = BackendActionMapper.toLocalAction(
            BackendActionItem("set_volume", mapOf("level" to 3.0)),
            "company_wifi"
        )
        assertEquals("set_volume", action.operation)
        assertEquals("company_wifi", action.trigger)
        assertEquals(20, action.params["value"])
    }

    @Test
    fun bluetoothEnabledMapsToLocalOperation() {
        val action = BackendActionMapper.toLocalAction(
            BackendActionItem("set_bluetooth", mapOf("enabled" to false)),
            null
        )
        assertEquals("close_bluetooth", action.operation)
    }

    @Test
    fun invalidBackendParametersDoNotReachExecutor() {
        assertThrows(IllegalArgumentException::class.java) {
            BackendActionMapper.toLocalAction(
                BackendActionItem("set_volume", mapOf("level" to 99.0)),
                null
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackendActionMapper.toLocalAction(
                BackendActionItem("create_rule", emptyMap()),
                null
            )
        }
    }
}
