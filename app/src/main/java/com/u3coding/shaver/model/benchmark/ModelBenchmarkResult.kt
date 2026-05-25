package com.u3coding.shaver.model.benchmark

import com.u3coding.shaver.model.runtime.LocalModelBackend

data class ModelBenchmarkResult(
    val backend: LocalModelBackend,
    val success: Boolean,
    val firstRunMs: Long,
    val avgRunMs: Long,
    val minRunMs: Long,
    val maxRunMs: Long,
    val error: String? = null
)