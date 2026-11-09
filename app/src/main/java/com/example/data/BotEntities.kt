package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bot_settings")
data class BotSettings(
    @PrimaryKey val id: Int = 1,
    val isBotRunning: Boolean = false,
    val privateKey: String = "2rUJTEkUDjuG9rxSfY4xE2u5Jsrg18EU3XqWzXhWxBaKoCHmVUVmY2pApF2rqTmpggNwJFEXuJZHjY1AHNtdKwcU",
    val rpcUrl: String = "https://mainnet.helius-rpc.com/?api-key=cbd6835a-5bc5-4c6a-85bb-4581534e4d87",
    val tradeAmountSol: Double = 0.08,
    val initialSetupBalance: Double = 0.08,
    val minProfitPercent: Double = 0.2,
    val tgBotToken: String = "8854945417:AAGVTegLowHl1NEpZY3RxpKbmU9TQZZjTVc",
    val tgChatId: String = "-1003936686948",
    val slippageBps: Int = 40,
    val isRealMode: Boolean = false,
    val jupiterApiUrl: String = "https://api.jup.ag/swap/v1",
    val jupiterApiKey: String = "jup_c3ab27714ff12cbc7d6169f3d11162f5cc751473825bfcd06ee5ec358c8703af",
    val useMultiRpc: Boolean = true,
    val jitoTipSol: Double = 0.001,
    val stopLossPercent: Double = 10.0,
    val targetProfitPercent: Double = 10.0,
    val targetTriggerPercent: Double = 26.0,
    val maxEntryDeviationPercent: Double = 2.0,
    val maxPoolAgeSeconds: Double = 120.0,
    val minMarketCapUsd: Double = 2000.0
)

@Entity(tableName = "trade_logs")
data class TradeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenSymbol: String,
    val buyDex: String,
    val sellDex: String,
    val buyPriceUsd: Double,
    val sellPriceUsd: Double,
    val profitSol: Double,
    val profitPercent: Double,
    val txSignature: String,
    val status: String, // "SUCCESS", "FAILED"
    val failureReason: String = ""
)

@Entity(tableName = "sniper_trades")
data class SniperTradeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val tokenSymbol: String,
    val tokenMint: String,
    val buyDex: String,
    val sellDex: String,
    val buyPriceUsd: Double,
    val sellPriceUsd: Double,
    val profitSol: Double,
    val profitPercent: Double,
    val txSignature: String,
    val isSimulation: Boolean,
    val ageSecondsAtBuy: Double,
    val status: String, // "SUCCESS", "FAILED", "PENDING"
    val failureReason: String = "",
    val marketCapAtBuy: Double = 0.0
)
