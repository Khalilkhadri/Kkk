package com.example.data

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.catch

class BotRepository(private val database: BotDatabase) {
    val settingsFlow: Flow<BotSettings?> = database.settingsDao().getSettingsFlow()
        .catch { e ->
            Log.e("BotRepository", "Error collecting settingsFlow: ${e.message}", e)
            emit(BotSettings())
        }

    val allLogsFlow: Flow<List<TradeLog>> = database.logDao().getAllLogsFlow()
        .catch { e ->
            Log.e("BotRepository", "Error collecting allLogsFlow: ${e.message}", e)
            emit(emptyList())
        }

    val sniperLogsFlow: Flow<List<SniperTradeLog>> = database.sniperLogDao().getAllSniperLogsFlow()
        .catch { e ->
            Log.e("BotRepository", "Error collecting sniperLogsFlow: ${e.message}", e)
            emit(emptyList())
        }

    suspend fun getSettings(): BotSettings {
        return try {
            val existing = database.settingsDao().getSettings()
            if (existing == null) {
                val defaultSettings = BotSettings()
                database.settingsDao().insertSettings(defaultSettings)
                defaultSettings
            } else {
                if (existing.jupiterApiKey == "jup_4fbcbd00466064007a29ec146edfa947a4a4abfdbe8e51c2be40ea9293a85368" ||
                    existing.jupiterApiKey == "jup_998ef44e8e4e4d79ef495db99ea0a01f7f5c9dd799c4e5541f7ad0aa78776b41" ||
                    existing.jupiterApiKey.trim().isEmpty() ||
                    existing.rpcUrl == "https://mainnet.helius-rpc.com/?api-key=47375d66-74c6-4dc2-a017-5fec80204025"
                ) {
                    val updated = existing.copy(
                        jupiterApiKey = "jup_c3ab27714ff12cbc7d6169f3d11162f5cc751473825bfcd06ee5ec358c8703af",
                        rpcUrl = "https://mainnet.helius-rpc.com/?api-key=cbd6835a-5bc5-4c6a-85bb-4581534e4d87"
                    )
                    database.settingsDao().insertSettings(updated)
                    updated
                } else {
                    existing
                }
            }
        } catch (e: Throwable) {
            Log.e("BotRepository", "getSettings Exception: ${e.message}")
            BotSettings()
        }
    }

    suspend fun saveSettings(settings: BotSettings) {
        try {
            database.settingsDao().insertSettings(settings)
        } catch (e: Throwable) {
            Log.e("BotRepository", "saveSettings Exception: ${e.message}")
        }
    }

    suspend fun updateRunningState(isRunning: Boolean) {
        try {
            val current = getSettings()
            saveSettings(current.copy(isBotRunning = isRunning))
        } catch (e: Throwable) {
            Log.e("BotRepository", "updateRunningState Exception: ${e.message}")
        }
    }

    suspend fun insertLog(log: TradeLog) {
        try {
            database.logDao().insertLog(log)
        } catch (e: Throwable) {
            Log.e("BotRepository", "insertLog Exception: ${e.message}")
        }
    }

    suspend fun clearAllLogs() {
        try {
            database.logDao().clearAllLogs()
        } catch (e: Throwable) {
            Log.e("BotRepository", "clearAllLogs Exception: ${e.message}")
        }
    }

    suspend fun insertSniperLog(log: SniperTradeLog) {
        try {
            database.sniperLogDao().insertSniperLog(log)
        } catch (e: Throwable) {
            Log.e("BotRepository", "insertSniperLog Exception: ${e.message}")
        }
    }

    suspend fun getPendingSniperLogForMint(mint: String): SniperTradeLog? {
        return try {
            database.sniperLogDao().getPendingSniperLogForMint(mint)
        } catch (e: Throwable) {
            Log.e("BotRepository", "getPendingSniperLogForMint Exception: ${e.message}")
            null
        }
    }

    suspend fun getSuccessfulTradedSymbols(): List<String> {
        return try {
            database.sniperLogDao().getSuccessfulTradedSymbols()
        } catch (e: Throwable) {
            Log.e("BotRepository", "getSuccessfulTradedSymbols Exception: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSuccessfulSniperTrades(): List<SniperTradeLog> {
        return try {
            database.sniperLogDao().getSuccessfulSniperTrades()
        } catch (e: Throwable) {
            Log.e("BotRepository", "getSuccessfulSniperTrades Exception: ${e.message}")
            emptyList()
        }
    }

    suspend fun clearAllSniperLogs() {
        try {
            database.sniperLogDao().clearAllSniperLogs()
        } catch (e: Throwable) {
            Log.e("BotRepository", "clearAllSniperLogs Exception: ${e.message}")
        }
    }
}
