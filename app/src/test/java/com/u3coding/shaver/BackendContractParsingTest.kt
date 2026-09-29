package com.u3coding.shaver

import com.google.gson.Gson
import com.u3coding.shaver.action.executor.BackendActionMapper
import com.u3coding.shaver.data.remote.AgentChatResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendContractParsingTest {
    @Test
    fun parsesDevicePlanFromBackendJson() {
        val json = """
            {
              "answer": "动作已通过校验",
              "intent": "DEVICE_CONTROL",
              "need_android_execute": true,
              "type": "action_plan",
              "action_plan": {
                "intent": "device_control",
                "need_android_execute": true,
                "actions": [{"action": "set_volume", "params": {"level": 3}}],
                "message": "请由 Android 执行",
                "skill_id": "device_control",
                "execution_target": "android",
                "requires_confirmation": false
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, AgentChatResponse::class.java)
        assertEquals("action_plan", response.type)
        assertTrue(response.action_plan!!.need_android_execute)
        val action = BackendActionMapper.toLocalAction(response.action_plan.actions.single(), null)
        assertEquals(20, action.params["value"])
    }

    @Test
    fun parsesTwoIndependentDeviceActionsInOrder() {
        val json = """
            {
              "answer": "动作已通过校验",
              "intent": "DEVICE_CONTROL",
              "need_android_execute": true,
              "type": "action_plan",
              "action_plan": {
                "intent": "device_control",
                "need_android_execute": true,
                "actions": [
                  {"action": "set_volume", "params": {"level": 5}},
                  {"action": "set_brightness", "params": {"value": 100}}
                ],
                "execution_target": "android",
                "requires_confirmation": false
              }
            }
        """.trimIndent()

        val response = Gson().fromJson(json, AgentChatResponse::class.java)
        val actions = response.action_plan!!.actions.map {
            BackendActionMapper.toLocalAction(it, null)
        }
        assertEquals(listOf("set_volume", "set_brightness"), actions.map { it.operation })
        assertEquals(100, actions[1].params["value"])
    }
}
