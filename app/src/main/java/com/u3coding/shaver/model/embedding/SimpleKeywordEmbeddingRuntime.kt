package com.u3coding.shaver.model.embedding

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Day 4 临时用的关键词 Embedding。
 *
 * 目标不是追求语义效果，而是先跑通：
 * 用户输入 → 向量化 → 相似度计算 → 检索 ActionSchema → PromptBuilder 注入 topK Action
 */
class SimpleKeywordEmbeddingRuntime : LocalEmbeddingRuntime {

    /**
     * 每一组代表一个语义维度。
     * 例如第 0 维代表“音量相关”，只要文本里出现“音量 / 声音 / 静音 / 喇叭”等词，就命中这一维。
     */
    private val semanticDimensions: List<KeywordDimension> = listOf(
        KeywordDimension(
            name = "volume",
            keywords = listOf("音量", "声音", "静音", "响铃", "铃声", "喇叭", "sound", "volume", "mute")
        ),
        KeywordDimension(
            name = "brightness",
            keywords = listOf("亮度", "屏幕", "太亮", "太暗", "brightness", "screen")
        ),
        KeywordDimension(
            name = "wifi",
            keywords = listOf("wifi", "wi-fi", "无线网", "网络", "公司wifi", "家里wifi", "办公室网络")
        ),
        KeywordDimension(
            name = "location_home",
            keywords = listOf("家", "到家", "回家", "家里")
        ),
        KeywordDimension(
            name = "location_company",
            keywords = listOf("公司", "办公室", "上班", "工位")
        ),
        KeywordDimension(
            name = "automation",
            keywords = listOf("自动", "以后", "后", "当", "如果", "每次", "进入", "连上", "连接")
        ),
        KeywordDimension(
            name = "increase",
            keywords = listOf("调高", "提高", "变大", "大一点", "高一点", "increase", "up")
        ),
        KeywordDimension(
            name = "decrease",
            keywords = listOf("调低", "降低", "变小", "小一点", "低一点", "安静", "decrease", "down")
        ),
        KeywordDimension(
            name = "set",
            keywords = listOf("设置", "设为", "调成", "改成", "帮我", "set")
        ),
        KeywordDimension(
            name = "open",
            keywords = listOf("打开", "开启", "启用", "open", "enable")
        ),
        KeywordDimension(
            name = "close",
            keywords = listOf("关闭", "关掉", "禁用", "close", "disable")
        ),
        KeywordDimension(
            name = "query",
            keywords = listOf("查询", "看看", "现在", "状态", "多少", "当前", "query", "status")
        ),
        KeywordDimension(
            name = "rule",
            keywords = listOf("规则", "创建规则", "自动化", "触发", "条件")
        ),
        KeywordDimension(
            name = "time",
            keywords = listOf("早上", "晚上", "中午", "夜间", "白天", "每天", "时间")
        ),
        KeywordDimension(
            name = "chat",
            keywords = listOf("你好", "介绍", "聊天", "你是谁", "能做什么", "hello")
        )
    )

    override suspend fun embed(text: String): EmbeddingResult = withContext(Dispatchers.Default) {
        val start = SystemClock.elapsedRealtime()

        try {
            val normalizedText = normalize(text)
            val vector = FloatArray(semanticDimensions.size)

            semanticDimensions.forEachIndexed { index, dimension ->
                vector[index] = calculateDimensionScore(
                    text = normalizedText,
                    keywords = dimension.keywords
                )
            }

            val latency = SystemClock.elapsedRealtime() - start

            EmbeddingResult(
                vector = vector,
                dimension = vector.size,
                latencyMs = latency,
                backend = "keyword_embedding",
                modelName = "simple_keyword_embedding_v1",
                error = null
            )
        } catch (e: Throwable) {
            val latency = SystemClock.elapsedRealtime() - start

            EmbeddingResult(
                vector = FloatArray(semanticDimensions.size),
                dimension = semanticDimensions.size,
                latencyMs = latency,
                backend = "keyword_embedding",
                modelName = "simple_keyword_embedding_v1",
                error = e.message
            )
        }
    }

    private fun normalize(text: String): String {
        return text
            .trim()
            .lowercase()
            .replace(" ", "")
    }

    /**
     * 计算某个语义维度的分数。
     *
     * 命中关键词越多，分数越高。
     * 这里做了一个简单上限，避免某一维过大。
     */
    private fun calculateDimensionScore(
        text: String,
        keywords: List<String>
    ): Float {
        if (text.isBlank()) return 0f

        var score = 0f

        keywords.forEach { keyword ->
            val normalizedKeyword = keyword.lowercase().replace(" ", "")
            if (text.contains(normalizedKeyword)) {
                score += 1f
            }
        }

        return score.coerceAtMost(3f)
    }

    private data class KeywordDimension(
        val name: String,
        val keywords: List<String>
    )
}