package com.u3coding.shaver

data class DeviceSettings(
    val wifiId: String,
    val notificationVolume: Int,
    val ringtoneVolume: Int,
    val screenBrightness: Int,
    val autoBrightness: Boolean
) {
    companion object {
        val DEFAULT = DeviceSettings(
            wifiId = "",
            notificationVolume = 50,
            ringtoneVolume = 50,
            screenBrightness = 50,
            autoBrightness = true
        )
    }
}
