package com.u3coding.shaver.model.runtime

interface LocalModelRuntime {

    fun classifyIntent(text: String): IntentResult

    fun embed(text: String): FloatArray

    fun getRuntimeInfo(): RuntimeInfo
}