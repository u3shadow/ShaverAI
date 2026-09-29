package com.u3coding.shaver

import com.u3coding.shaver.action.parser.BackendRuleDraftMapper
import com.u3coding.shaver.data.remote.AgentChatResponse
import com.u3coding.shaver.data.remote.BackendActionItem
import com.u3coding.shaver.data.remote.BackendActionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackendRuleDraftMapperTest {
    @Test
    fun gsonNumericValuesBecomeIntegersForRuleValidation() {
        val response = AgentChatResponse(
            type = "confirmation_required",
            action_plan = BackendActionPlan(
                intent = "rule_create",
                need_android_execute = false,
                actions = listOf(
                    BackendActionItem(
                        "create_rule",
                        mapOf(
                            "name" to "Office quiet mode",
                            "trigger" to mapOf(
                                "type" to "wifi_connected",
                                "params" to mapOf("ssid" to "company_wifi")
                            ),
                            "action" to mapOf(
                                "type" to "set_volume",
                                "params" to mapOf("level" to 0.0)
                            )
                        )
                    )
                ),
                execution_target = "backend",
                requires_confirmation = true
            )
        )

        val request = BackendRuleDraftMapper.fromResponse(response, "user_1")
        assertNotNull(request)
        assertEquals("user_1", request!!.user_id)
        val actionParams = request.action["params"] as Map<*, *>
        assertEquals(0, actionParams["level"])
    }
}
