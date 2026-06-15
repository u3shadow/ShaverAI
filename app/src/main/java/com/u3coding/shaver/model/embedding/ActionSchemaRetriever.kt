package com.u3coding.shaver.model.embedding

interface ActionSchemaRetriever {
    suspend fun retrieve(text: String, topK: Int = 5): List<RetrievalResult>
}