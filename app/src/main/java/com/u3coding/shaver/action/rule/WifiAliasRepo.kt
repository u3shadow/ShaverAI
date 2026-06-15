package com.u3coding.shaver.action.rule

import com.u3coding.shaver.data.local.RuleDatabase
import com.u3coding.shaver.data.local.WifiAliasEntity

class WifiAliasRepo {
    companion object {
        @Volatile
        private var databaseProvider: (() -> RuleDatabase)? = null

        fun init(provider: () -> RuleDatabase) {
            databaseProvider = provider
        }

        fun saveAlias(ssid: String, alias: String) {
            val cleanSsid = ssid.trim()
            val cleanAlias = alias.trim()
            if (cleanSsid.isBlank() || cleanAlias.isBlank()) return
            val now = System.currentTimeMillis()
            val old = findBySsid(cleanSsid)
            dao().upsert(
                WifiAliasEntity(
                    cleanSsid,
                    cleanAlias,
                    old?.createdAt ?: now,
                    now
                )
            )
        }

        fun getAllAliases(): List<WifiAlias> {
            return dao().all.map {
                WifiAlias(
                    ssid = it.ssid,
                    alias = it.alias
                )
            }
        }

        fun findAliasBySsid(ssid: String): String? {
            return findBySsid(ssid)?.alias?.takeIf { it.isNotBlank() }
        }

        fun resolveTargetToSsid(target: String?): String? {
            val cleanTarget = target?.trim()?.takeIf { it.isNotBlank() } ?: return null
            return dao().findSsidByAlias(cleanTarget)?.takeIf { it.isNotBlank() } ?: cleanTarget
        }

        fun deleteBySsid(ssid: String) {
            dao().deleteBySsid(ssid)
        }

        private fun findBySsid(ssid: String): WifiAliasEntity? {
            return dao().findBySsid(ssid)
        }

        private fun dao() = checkNotNull(databaseProvider) {
            "WifiAliasRepo is not initialized. Call RuleRepo.init(context) first."
        }.invoke().wifiAliasDao()
    }
}

data class WifiAlias(
    val ssid: String,
    val alias: String
)
