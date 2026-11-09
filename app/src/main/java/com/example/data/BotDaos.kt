package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Query("SELECT * FROM bot_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<BotSettings?>

    @Query("SELECT * FROM bot_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): BotSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: BotSettings)

    @Query("UPDATE bot_settings SET isBotRunning = :isRunning WHERE id = 1")
    suspend fun updateRunningState(isRunning: Boolean)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM trade_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<TradeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: TradeLog)

    @Query("DELETE FROM trade_logs")
    suspend fun clearAllLogs()
}

@Dao
interface SniperLogDao {
    @Query("SELECT * FROM sniper_trades ORDER BY timestamp DESC")
    fun getAllSniperLogsFlow(): Flow<List<SniperTradeLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSniperLog(log: SniperTradeLog)

    @Query("SELECT * FROM sniper_trades WHERE tokenMint = :mint AND status = 'PENDING' LIMIT 1")
    suspend fun getPendingSniperLogForMint(mint: String): SniperTradeLog?

    @Query("SELECT DISTINCT tokenSymbol FROM sniper_trades WHERE status = 'SUCCESS'")
    suspend fun getSuccessfulTradedSymbols(): List<String>

    @Query("SELECT * FROM sniper_trades WHERE status = 'SUCCESS'")
    suspend fun getSuccessfulSniperTrades(): List<SniperTradeLog>

    @Query("DELETE FROM sniper_trades")
    suspend fun clearAllSniperLogs()
}
