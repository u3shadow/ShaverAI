package com.u3coding.shaver.model

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class LocalIntentModelProvider(
    private val context: Context? = null,
    private val featureExtractor: IntentFeatureExtractor = IntentFeatureExtractor()
) {

    private val interpreter: Interpreter? by lazy {
        val modelBuffer = loadModelFile(MODEL_FILE_NAME) ?: return@lazy null
        runCatching { Interpreter(modelBuffer) }
            .onFailure { e ->
                Log.e(TAG, "Failed to create local intent interpreter: ${e.message}", e)
            }
            .getOrNull()
    }

    fun predict(text: String): LocalIntentResult {
        val model = interpreter ?: return fallbackResult()

        return runCatching {
            predictWithModel(model, text)
        }.onFailure { e ->
            Log.e(TAG, "Failed to predict local intent: ${e.message}", e)
        }.getOrElse {
            fallbackResult()
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer? {
        val appContext = context ?: run {
            Log.w(TAG, "No context provided. Local intent model is disabled.")
            return null
        }

        val exists = runCatching {
            appContext.assets.list("")?.contains(fileName) == true
        }.getOrDefault(false)

        if (!exists) {
            Log.w(TAG, "Model file $fileName not found in assets. Local intent model is disabled.")
            return null
        }

        return runCatching {
            appContext.assets.openFd(fileName).use { fileDescriptor ->
                FileInputStream(fileDescriptor.fileDescriptor).channel.use { fileChannel ->
                    fileChannel.map(
                        FileChannel.MapMode.READ_ONLY,
                        fileDescriptor.startOffset,
                        fileDescriptor.declaredLength
                    )
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to load model file: ${e.message}", e)
        }.getOrNull()
    }

    private fun predictWithModel(interpreter: Interpreter, text: String): LocalIntentResult {
        val input = arrayOf(featureExtractor.extract(text))
        val output = Array(1) { FloatArray(3) }

        interpreter.run(input, output)

        val scores = output[0]
        val maxIndex = scores.indices.maxBy { scores[it] }
        val confidence = scores[maxIndex]

        val intent = when (maxIndex) {
            0 -> LocalIntent.NormalChat
            1 -> LocalIntent.DeviceControl
            2 -> LocalIntent.RuleGeneration
            else -> LocalIntent.NormalChat
        }

        return LocalIntentResult(intent, confidence)
    }

    private fun fallbackResult(): LocalIntentResult {
        return LocalIntentResult(
            intent = LocalIntent.NormalChat,
            confidence = 0f
        )
    }

    companion object {
        private const val TAG = "LocalIntentModelProvider"
        private const val MODEL_FILE_NAME = "intent_model.tflite"
    }
}
