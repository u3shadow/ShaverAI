package com.u3coding.shaver.model.embedding

interface LocalEmbeddingRuntime {
    suspend fun embed(text: String): EmbeddingResult
}