package com.u3coding.shaver.model.embedding

object CosineSimilarity {

    fun calculate(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size) return 0f

        var dot = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        if (normA == 0f || normB == 0f) return 0f

        return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)))
    }
}