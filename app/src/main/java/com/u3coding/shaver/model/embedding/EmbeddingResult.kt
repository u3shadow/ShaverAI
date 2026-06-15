package com.u3coding.shaver.model.embedding

data class EmbeddingResult(
    val vector: FloatArray,
    val dimension: Int,
    val latencyMs: Long,
    val backend: String,
    val modelName: String,
    val error: String? = null
)