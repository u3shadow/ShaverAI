package com.u3coding.shaver.model.benchmark

import android.content.Context
import com.u3coding.shaver.model.LocalIntentModelProvider
import com.u3coding.shaver.model.runtime.LocalModelBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalModelBenchmark(
    private val context: Context
) {
    suspend fun runAll(): List<ModelBenchmarkResult> {
        val testInputs = listOf(
            "你好，你能做什么",
            "帮我把音量调低",
            "把亮度调高",
            "连上公司 WiFi 后帮我静音",
            "晚上自动调低音量"
        )

        return listOf(
            LocalModelBackend.CPU,
            LocalModelBackend.NNAPI,
            LocalModelBackend.GPU
        ).map { backend ->
            run(
                backend = backend,
                testInputs = testInputs,
                repeatCount = 20
            )
        }
    }
    suspend fun run(
        backend: LocalModelBackend,
        testInputs: List<String>,
        repeatCount: Int = 20
    ): ModelBenchmarkResult = withContext(Dispatchers.Default) {
        val provider = LocalIntentModelProvider(
            context = context,
            backend = backend
        )

        try {
            val latencies = mutableListOf<Long>()

            repeat(repeatCount) { index ->
                val input = testInputs[index % testInputs.size]
                val result = provider.predict(input)
                latencies.add(result.latencyMs)
            }

            ModelBenchmarkResult(
                backend = backend,
                success = true,
                firstRunMs = latencies.firstOrNull() ?: 0L,
                avgRunMs = latencies.average().toLong(),
                minRunMs = latencies.minOrNull() ?: 0L,
                maxRunMs = latencies.maxOrNull() ?: 0L,
                error = null
            )
        } catch (e: Throwable) {
            ModelBenchmarkResult(
                backend = backend,
                success = false,
                firstRunMs = 0L,
                avgRunMs = 0L,
                minRunMs = 0L,
                maxRunMs = 0L,
                error = e.message
            )
        } finally {
            provider.close()
        }
    }
}