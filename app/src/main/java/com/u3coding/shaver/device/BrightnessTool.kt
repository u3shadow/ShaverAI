package com.u3coding.shaver.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlin.math.pow
import kotlin.math.roundToInt

class BrightnessTool(val context: Context) {
    fun applyBrightness(level: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            return false
        }

        val clamped = level.coerceIn(0, 100)
        val normalized = clamped / 100f
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        // Huawei/Honor slider curve is noticeably non-linear relative to raw 0..255 values.
        // Use a perceptual-like curve to better match slider percentage expectation on those devices.
        val mapped = if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            normalized.toDouble().pow(2.2).toFloat()
        } else {
            normalized
        }
        val brightness = (mapped * 255f).roundToInt().coerceIn(0, 255)

        val updated = Settings.System.putInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            brightness
        )

        Log.i(
            "BrightnessTool",
            "set brightness to $clamped, manufacturer=$manufacturer, actual brightness=$brightness"
        )
        return updated
    }
}
