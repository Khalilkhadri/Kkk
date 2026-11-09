package com.example.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BotStateManager {
    val isScanning = MutableStateFlow(false)
    val scanCount = MutableStateFlow(0)
    val isRpcConnected = MutableStateFlow(true)
    val solPriceUsd = MutableStateFlow(185.0)
    val walletBalance = MutableStateFlow(4.825)
    val allocatedCapital = MutableStateFlow(0.08)
    val livePrices = MutableStateFlow<List<LiveTokenPrice>>(emptyList())
    val statusText = MutableStateFlow("مستعد للبدء")
    val isApiBlocked = MutableStateFlow(false)
    
    val isServiceRunning = MutableStateFlow(false)
    val lastFailedTokenMint = MutableStateFlow("")

    // Sniper Mode States
    val isSniperRunning = MutableStateFlow(false)
    val isSniperSimMode = MutableStateFlow(true)
    val sniperCapital = MutableStateFlow(2.0) // Virtual or Real SOL dedicated for sniper
    val trackedSniperTokens = MutableStateFlow<List<SniperTokenTrack>>(emptyList())
    val sniperStatusText = MutableStateFlow("قناص المجمعات مستعد للبدء...")

    val appReports = MutableStateFlow<List<AppReport>>(emptyList())

    fun addReport(type: ReportType, title: String, message: String, details: String? = null) {
        val newReport = AppReport(type = type, title = title, message = message, details = details)
        val current = appReports.value.toMutableList()
        current.add(0, newReport)
        if (current.size > 100) {
            appReports.value = current.take(100)
        } else {
            appReports.value = current
        }
    }

    fun addReportFromTelegram(htmlText: String) {
        try {
            // Strip HTML tags for clean rendering
            val cleanText = htmlText
                .replace(Regex("<br/?>"), "\n")
                .replace(Regex("<[^>]*>"), "")
                .trim()
            
            // Extract title as the first line or first 100 chars
            val lines = cleanText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            val title = if (lines.isNotEmpty()) lines[0] else "تقرير تقني للنظام"
            val message = if (lines.size > 1) lines.drop(1).joinToString("\n") else cleanText
            
            // Auto categorize based on keywords
            val type = when {
                htmlText.contains("نجاح", ignoreCase = true) || htmlText.contains("SUCCESS", ignoreCase = true) -> ReportType.TRADE_SUCCESS
                htmlText.contains("فشل", ignoreCase = true) || htmlText.contains("FAILED", ignoreCase = true) -> ReportType.TRADE_FAILED
                htmlText.contains("Guard", ignoreCase = true) || htmlText.contains("الحماية", ignoreCase = true) || htmlText.contains("استبعاد", ignoreCase = true) -> ReportType.SECURITY
                htmlText.contains("⚠️") || htmlText.contains("خطأ") || htmlText.contains("حظر") -> ReportType.WARNING
                else -> ReportType.INFO
            }
            
            addReport(type, title, message)
        } catch (e: Exception) {
            android.util.Log.e("BotStateManager", "Error adding local report from Telegram msg: ${e.message}")
        }
    }

    fun getSupportedTokens(): List<Pair<String, String>> {
        return listOf(
            "JitoSOL" to "J1toso1e67Ad6nd9Lk7sW1ED89Dpk25fK61YF4re7mY",
            "mSOL" to "mSoLzY97Gp4v9ih3LxKeK2LE7P3hS99cn35lDATUuRY",
            "WIF" to "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm",
            "BONK" to "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
            "TRUMP" to "6p6xgHyF7AeE6TZkSmFsko444wqoP15icUSqi2jfGiPN",
            "MEW" to "MEW1gQWJ3nEXg2qgERiKu7FAFj79PHvQVREQUzScPP5",
            "GOAT" to "CzLSujWBLFsSjncfkh59rUFqvafWcY5tzedWJSuypump",
            "PNUT" to "2qEHjDLDLbuBgRYvsxhc5D6uDWAivNFZGan56P1tpump",
            "RENDER" to "rndr8V4mveZgKTf6EgU69m87Xp9gU6goxp7bT7Tiump",
            "CHILLGUY" to "Df6yfrKC8kZE3KNkrHERKzAetSxbrWeniQfyJY4Jez6P",
            "RAY" to "4k3DyjzvNcz84bWUiE9x3C49E2f5pC5eWyDgDTscumud",
            "BOME" to "uk3A7b7q2ax9vRT28STb65g3YgPToX9A174mGfWpump",
            "PYTH" to "HZ1Jb9NSVxsqTMu6CQW2To957U2u6ZCHvMuJaXNfgEWy",
            "DRIFT" to "DrifeZZCdP7i6auG1vT83C1SgscKyASt8uxKbcS762SK",
            "MOODENG" to "ED5nyv9VsaJ6g7mLsAcu7BpBNpd9v8Yt6WczFToFaTa8",
            "GIGA" to "6327613bF3FE1B12bbF34cDd95f77BA66feE3d2d",
            "FARTCOIN" to "9BB6NGo75e1Z45ZwgHctEws3ZgQpHf5GicTWzGxypump",
            "AI16Z" to "HeLp6NuEwLc7gBsY7X8XdfRrL7zN6y8aeqfXfV7SgZz2",
            "GRASS" to "GrassmSy6q6x6x6x6x6x6x6x6x6x6x6x6x6x6x6x6x6x",
            "JTO" to "jtojtomepa2be6ed1wmdgfcunmsbgeftn6kcl9vcl7u",
            "TNSR" to "TNSRxcU6v9BeYr6m9S4EPrv9is4NfP1icusM8Ew4vE8",
            "ORCA" to "orcaS1Yv99x7rbj6Dfshg8ndGx97z5g5CSU7N6VbFtx"
        )
    }
}

data class SniperTokenTrack(
    val id: String = java.util.UUID.randomUUID().toString(), // cached bust trigger comment
    val symbol: String,
    val name: String,
    val mint: String,
    val createdAt: Long, // timestamp of inception/detection
    var raydiumPriceUsd: Double,
    var meteoraPriceUsd: Double,
    val initialRaydiumPriceUsd: Double,
    val initialMeteoraPriceUsd: Double,
    var lastUpdated: Long = System.currentTimeMillis(),
    val launchpad: String = "Raydium & Meteora Dual Launch",
    var currentChangePercent: Double = 0.0, // appreciation from inception
    var hasBought: Boolean = false,
    var buyPrice: Double = 0.0,
    var buyDex: String = "",
    var isTradeClosed: Boolean = false,
    val ageSecondsAtInception: Double = 15.0,
    var simTradeAmountSol: Double = 0.15,
    val trendType: Int = kotlin.random.Random.nextInt(100), // 0-14: Bullish (~15%), 15-54: Bearish (~40%), 55-99: Neutral (~45%)
    var boughtTokenAmountLamports: Long = 0L,
    var buyChangePercent: Double = 0.0,
    var boughtAt: Long = 0L,
    var isSecurityScanned: Boolean = false,
    var isSecuritySafe: Boolean = true,
    var securityDangerReason: String? = null,
    var mintAuthorityEnabled: Boolean = false,
    var freezeAuthorityEnabled: Boolean = false,
    var topHolderPercent: Double = 0.0,
    var devWalletPercent: Double = 0.0,
    val changePercentHistory: List<Double> = emptyList(),
    val changeHistoryTimestamps: List<Long> = emptyList(),
    val totalSupply: Double = 1_000_000_000.0,
    val creatorAddress: String = "",
    val isSimulationMode: Boolean = false,
    val isBuyConfirmed: Boolean = false,
    val isSellTriggered: Boolean = false,
    val sellTriggeredAt: Long = 0L,
    val sellDexSelected: String = "",
    val buyPriceConfirmed: Double = 0.0,
    val peakProfitPercentSinceBuy: Double = 0.0
) {
    val marketCapUsd: Double
        get() = (if (raydiumPriceUsd > 0.0) raydiumPriceUsd else meteoraPriceUsd) * totalSupply
}

enum class ReportType {
    ERROR, WARNING, INFO, SECURITY, TRADE_SUCCESS, TRADE_FAILED
}

data class AppReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val type: ReportType,
    val title: String,
    val message: String,
    val details: String? = null
)


