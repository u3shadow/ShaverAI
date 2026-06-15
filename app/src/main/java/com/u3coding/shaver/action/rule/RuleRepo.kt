package com.u3coding.shaver.action.rule

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.u3coding.shaver.action.model.Action
import com.u3coding.shaver.data.local.RuleDatabase
import com.u3coding.shaver.data.local.RuleEntity

class RuleRepo {
    companion object {
        @Volatile
        private var database: RuleDatabase? = null

        fun init(context: Context) {
            if (database != null) return
            synchronized(this) {
                if (database == null) {
                    database = Room.databaseBuilder(
                        context.applicationContext,
                        RuleDatabase::class.java,
                        "rule_store.db"
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                        .allowMainThreadQueries()
                        .build()
                }
                WifiAliasRepo.init { requireDatabase() }
            }
        }

        fun addRule(action: Action) {
            val dao = requireDao()
            val value = action.params["value"]?.toString()
            dao.upsert(
                RuleEntity(
                    action.trigger,
                    action.operation,
                    RuleEventType.normalize(action.eventType),
                    value
                )
            )
        }

        fun getRules(
            ssid: String,
            eventType: String = RuleEventType.WIFI_CONNECTED
        ): List<Action> {
            val dao = requireDao()
            return dao.getByTriggerAndEvent(ssid, RuleEventType.normalize(eventType)).map { it.toAction() }
        }

        fun getAllSceneSsids(): List<String> {
            return requireDao().getAllTriggers()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }

        fun replaceRules(
            ssid: String,
            eventType: String,
            actions: List<Action>
        ) {
            val normalizedEventType = RuleEventType.normalize(eventType)
            val dao = requireDao()
            dao.deleteByTriggerAndEvent(ssid, normalizedEventType)
            actions.forEach { action ->
                addRule(action.copy(trigger = ssid, eventType = normalizedEventType))
            }
        }

        fun deleteScene(ssid: String) {
            requireDao().deleteByTrigger(ssid)
            WifiAliasRepo.deleteBySsid(ssid)
        }

        private fun requireDatabase() = checkNotNull(database) {
            "RuleRepo is not initialized. Call RuleRepo.init(context) first."
        }

        private fun requireDao() = requireDatabase().ruleDao()

        private fun RuleEntity.toAction(): Action {
            val params = mutableMapOf<String, Any>()
            parseValue(value)?.let { params["value"] = it }
            return Action(
                trigger = trigger,
                operation = operation,
                params = params,
                eventType = RuleEventType.normalize(eventType)
            )
        }

        private fun parseValue(raw: String?): Any? {
            if (raw.isNullOrBlank()) return null
            raw.toIntOrNull()?.let { return it }
            raw.toDoubleOrNull()?.let { return it }
            return raw
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE rules ADD COLUMN event_type TEXT NOT NULL DEFAULT '${RuleEventType.WIFI_CONNECTED}'"
                )
                database.execSQL("DROP INDEX IF EXISTS index_rules_trigger_operation")
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_rules_trigger_event_type_operation " +
                        "ON rules(trigger, event_type, operation)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS wifi_aliases (" +
                        "ssid TEXT NOT NULL, " +
                        "alias TEXT NOT NULL, " +
                        "created_at INTEGER NOT NULL, " +
                        "updated_at INTEGER NOT NULL, " +
                        "PRIMARY KEY(ssid))"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_wifi_aliases_alias ON wifi_aliases(alias)"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_wifi_aliases_ssid ON wifi_aliases(ssid)"
                )
            }
        }
    }
}
