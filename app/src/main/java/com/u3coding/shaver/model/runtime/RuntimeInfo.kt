package com.u3coding.shaver.model.runtime

data class RuntimeInfo(
    val framework: String,
    val modelName: String,
    val acceleration: String,
    val available: Boolean
)