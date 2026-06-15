package com.u3coding.shaver.model.embedding

object ActionSchemaRegistry {

    val schemas: List<ActionSchema> = listOf(
        ActionSchema(
            actionId = "set_volume",
            description = "调整系统音量大小，可以调高、调低、静音或设置到指定数值",
            examples = listOf(
                "帮我把音量调低",
                "声音太大了",
                "调成静音",
                "把音量设置成 30"
            ),
            paramSchema = mapOf(
                "value" to "音量值，范围 0-100"
            ),
            riskLevel = ActionRiskLevel.LOW
        ),

        ActionSchema(
            actionId = "set_brightness",
            description = "调整屏幕亮度，可以调高、调低或设置到指定数值",
            examples = listOf(
                "把亮度调高",
                "屏幕太亮了",
                "亮度调低一点",
                "设置亮度为 50"
            ),
            paramSchema = mapOf(
                "value" to "亮度值，范围 0-100"
            ),
            riskLevel = ActionRiskLevel.LOW
        ),

        ActionSchema(
            actionId = "create_wifi_rule",
            description = "根据 WiFi、地点、时间等条件创建自动化规则",
            examples = listOf(
                "连上公司 WiFi 后帮我静音",
                "到家后自动调高音量",
                "进入办公室网络后关闭声音"
            ),
            paramSchema = mapOf(
                "trigger" to "触发条件，例如 wifi_connected",
                "action" to "触发后执行的动作"
            ),
            riskLevel = ActionRiskLevel.MEDIUM
        )
    )
}