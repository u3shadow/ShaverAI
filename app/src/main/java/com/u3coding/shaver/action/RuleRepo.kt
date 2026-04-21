package com.u3coding.shaver.action

import android.content.Context
import androidx.room.Room
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
                        .allowMainThreadQueries()
                        .build()
                }
            }
        }

        fun addRule(action: Action) {
            val dao = requireDao()
            val value = action.params["value"]?.toString()
            dao.upsert(
                RuleEntity(
                    action.trigger,
                    action.operation,
                    value
                )
            )
        }

        fun getRules(ssid: String): List<Action> {
            val dao = requireDao()
            return dao.getByTrigger(ssid).map { it.toAction() }
        }

        fun getAllRulesGrouped(): Map<String, List<Action>> {
            val dao = requireDao()
            return dao.getAll()
                .groupBy { it.trigger }
                .mapValues { (_, value) -> value.map { it.toAction() } }
        }

        fun getAllTriggers(): List<String> {
            return requireDao().getAllTriggers()
        }

        private fun requireDao() = checkNotNull(database) {
            "RuleRepo is not initialized. Call RuleRepo.init(context) first."
        }.ruleDao()

        private fun RuleEntity.toAction(): Action {
            val params = mutableMapOf<String, Any>()
            parseValue(value)?.let { params["value"] = it }
            return Action(
                trigger = trigger,
                operation = operation,
                params = params
            )
        }

        private fun parseValue(raw: String?): Any? {
            if (raw.isNullOrBlank()) return null
            raw.toIntOrNull()?.let { return it }
            raw.toDoubleOrNull()?.let { return it }
            return raw
        }
    }
}
