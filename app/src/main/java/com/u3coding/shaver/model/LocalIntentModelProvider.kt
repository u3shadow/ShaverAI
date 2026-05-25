package com.u3coding.shaver.model

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.u3coding.shaver.model.runtime.LocalModelBackend
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

class LocalIntentModelProvider(
    private val context: Context? = null,
    private val featureExtractor: IntentFeatureExtractor = IntentFeatureExtractor(),
    private val backend: LocalModelBackend = LocalModelBackend.CPU
) {
    private var actualBackend: String = backend.name
    private var gpuDelegate: GpuDelegate? = null
    private val interpreter: Interpreter? by lazy {
        val options = Interpreter.Options()

        when (backend) {
            LocalModelBackend.CPU -> {
                options.setNumThreads(2)
                actualBackend = "cpu"
            }

            LocalModelBackend.NNAPI -> {
                options.setUseNNAPI(true)
                options.setNumThreads(2)
                actualBackend = "nnapi"
            }

            LocalModelBackend.GPU -> {
                val compatList = CompatibilityList()

                if (compatList.isDelegateSupportedOnThisDevice) {
                    gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
                    options.addDelegate(gpuDelegate)
                    actualBackend = "GPU"
                } else {
                    options.setNumThreads(2)
                    actualBackend = "back_up_cpu"
                }
            }
        }
        val modelBuffer = loadModelFile(MODEL_FILE_NAME) ?: return@lazy null
        runCatching { Interpreter(modelBuffer,options) }
            .onFailure { e ->
                Log.e(TAG, "Failed to create local intent interpreter: ${e.message}", e)
            }
            .getOrNull()
    }
    fun close() {
        runCatching {
            interpreter?.close()
        }.onFailure { e ->
            Log.e(TAG, "Failed to close interpreter: ${e.message}", e)
        }

        runCatching {
            gpuDelegate?.close()
        }.onFailure { e ->
            Log.e(TAG, "Failed to close gpuDelegate: ${e.message}", e)
        }

        gpuDelegate = null
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
        val startNs = SystemClock.elapsedRealtimeNanos()

        val input = arrayOf(featureExtractor.extract(text))
        val output = Array(1) { FloatArray(3) }

        interpreter.run(input, output)

        val scores = output[0]
        val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: 0
        val confidence = scores[maxIndex]

        val intent = when (maxIndex) {
            0 -> LocalIntent.NormalChat
            1 -> LocalIntent.DeviceControl
            2 -> LocalIntent.RuleGeneration
            else -> LocalIntent.Unknown
        }

        val latencyMs = TimeUnit.NANOSECONDS.toMillis(
            SystemClock.elapsedRealtimeNanos() - startNs
        )

        return LocalIntentResult(
            intent = intent,
            confidence = confidence,
            latencyMs = latencyMs,
            backend = actualBackend
        )
    }


    private fun fallbackResult(): LocalIntentResult {
        return LocalIntentResult(
            intent = LocalIntent.NormalChat,
            confidence = 0f,
        )
    }

    companion object {
        private const val TAG = "LocalIntentModelProvider"
        private const val MODEL_FILE_NAME = "intent_model.tflite"
    }
}
