package com.u3coding.shaver.model.runtime

import com.u3coding.shaver.model.LocalIntent
import com.u3coding.shaver.model.LocalIntentModelProvider

class TfliteLocalModelRuntime(
    private val provider: LocalIntentModelProvider
) : LocalModelRuntime {

    override  fun classifyIntent(text: String): IntentResult {
        val result = provider.predict(text)

        return IntentResult(
            intent = when (result.intent) {
                LocalIntent.NormalChat -> IntentType.NORMAL_CHAT
                LocalIntent.DeviceControl -> IntentType.DEVICE_CONTROL
                LocalIntent.RuleGeneration -> IntentType.RULE_CREATE
            },
            confidence = result.confidence,
            source = "tflite_intent_model"
        )
    }

    override  fun embed(text: String): FloatArray {
        // Day 8 先占位，不接真实 embedding 模型
        return FloatArray(0)
    }

    override fun getRuntimeInfo(): RuntimeInfo {
        return RuntimeInfo(
            framework = "TFLite",
            modelName = "intent_model.tflite",
            acceleration = "CPU",
            available = true
        )
    }
}