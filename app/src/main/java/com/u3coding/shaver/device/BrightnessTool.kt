package com.u3coding.shaver.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlin.math.max
import kotlin.math.min

class BrightnessTool(val context: Context) {
    //根据传入参数修改亮度
     fun applyBrightness(level: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            return
        }
        val level = Math.max(1, Math.min(level, 100))
        val brightness = max(1, min((255f * (level / 100f)).toInt(), 255))
        Settings.System.putInt(
            context.getContentResolver(),
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
        )
        Settings.System.putInt(
            context.getContentResolver(),
            Settings.System.SCREEN_BRIGHTNESS,
            brightness
        )
        Log.e("BrightnessTool", "set brightness to $level, actual brightness: $brightness")
    }
}