package com.u3coding.shaver

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SettingsDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SETTINGS (
                id INTEGER PRIMARY KEY,
                wifi_id TEXT NOT NULL,
                notification_volume INTEGER NOT NULL,
                ringtone_volume INTEGER NOT NULL,
                screen_brightness INTEGER NOT NULL,
                auto_brightness INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SETTINGS")
        onCreate(db)
    }

    fun saveSettings(settings: DeviceSettings) {
        val values = ContentValues().apply {
            put(COLUMN_ID, SINGLE_ROW_ID)
            put(COLUMN_WIFI_ID, settings.wifiId)
            put(COLUMN_NOTIFICATION_VOLUME, settings.notificationVolume)
            put(COLUMN_RINGTONE_VOLUME, settings.ringtoneVolume)
            put(COLUMN_SCREEN_BRIGHTNESS, settings.screenBrightness)
            put(COLUMN_AUTO_BRIGHTNESS, if (settings.autoBrightness) 1 else 0)
        }

        writableDatabase.insertWithOnConflict(
            TABLE_SETTINGS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun loadSettings(): DeviceSettings? {
        val cursor = readableDatabase.query(
            TABLE_SETTINGS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(SINGLE_ROW_ID.toString()),
            null,
            null,
            null
        )

        cursor.use {
            if (!it.moveToFirst()) {
                return null
            }

            return DeviceSettings(
                wifiId = it.getString(it.getColumnIndexOrThrow(COLUMN_WIFI_ID)),
                notificationVolume = it.getInt(it.getColumnIndexOrThrow(COLUMN_NOTIFICATION_VOLUME)),
                ringtoneVolume = it.getInt(it.getColumnIndexOrThrow(COLUMN_RINGTONE_VOLUME)),
                screenBrightness = it.getInt(it.getColumnIndexOrThrow(COLUMN_SCREEN_BRIGHTNESS)),
                autoBrightness = it.getInt(it.getColumnIndexOrThrow(COLUMN_AUTO_BRIGHTNESS)) == 1
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "shaver_settings.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SETTINGS = "settings"

        private const val COLUMN_ID = "id"
        private const val COLUMN_WIFI_ID = "wifi_id"
        private const val COLUMN_NOTIFICATION_VOLUME = "notification_volume"
        private const val COLUMN_RINGTONE_VOLUME = "ringtone_volume"
        private const val COLUMN_SCREEN_BRIGHTNESS = "screen_brightness"
        private const val COLUMN_AUTO_BRIGHTNESS = "auto_brightness"

        private const val SINGLE_ROW_ID = 1
    }
}
