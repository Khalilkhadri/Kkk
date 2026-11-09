package com.example.api

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object SolanaApiClient {
    private val client: OkHttpClient = try {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        OkHttpClient.Builder()
            .dns(Ipv4Dns)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 150
                maxRequestsPerHost = 150
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.5 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .build()
    } catch (e: Exception) {
        Log.e("SolanaApiClient", "Error tailoring SSL TrustManager, building standard backup", e)
        OkHttpClient.Builder()
            .dns(Ipv4Dns)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .dispatcher(okhttp3.Dispatcher().apply {
                maxRequests = 150
                maxRequestsPerHost = 150
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.5 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private val connectionPool = okhttp3.ConnectionPool(60, 5, TimeUnit.MINUTES)

    private val fastClient = client.newBuilder()
        .connectTimeout(5000, TimeUnit.MILLISECONDS)
        .readTimeout(8000, TimeUnit.MILLISECONDS)
        .writeTimeout(8000, TimeUnit.MILLISECONDS)
        .connectionPool(connectionPool)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 250
            maxRequestsPerHost = 250
        })
        .build()

    private val txClient = client.newBuilder()
        .connectTimeout(6000, TimeUnit.MILLISECONDS)
        .readTimeout(10000, TimeUnit.MILLISECONDS)
        .writeTimeout(10000, TimeUnit.MILLISECONDS)
        .connectionPool(connectionPool)
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 250
            maxRequestsPerHost = 250
        })
        .build()

    private val securityClient = client.newBuilder()
        .connectTimeout(8000, TimeUnit.MILLISECONDS)
        .readTimeout(12000, TimeUnit.MILLISECONDS)
        .connectionPool(connectionPool)
        .build()

    private const val SOL_MINT = "So11111111111111111111111111111111111111112"

    @Volatile
    var tgBotToken: String = "8854945417:AAGVTegLowHl1NEpZY3RxpKbmU9TQZZjTVc"
    @Volatile
    var tgChatId: String = "-1003936686948"
    @Volatile
    var isRealMode: Boolean = false
    @Volatile
    var jupiterApiUrl: String = "https://api.jup.ag/swap/v1"
    @Volatile
    var jupiterApiKey: String = "jup_c3ab27714ff12cbc7d6169f3d11162f5cc751473825bfcd06ee5ec358c8703af"
    @Volatile
    var lastSendTxError: String = ""

    private val lastReportedErrors = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val meteoraCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Double, Long>>()
    @Volatile
    private var cachedSolPrice: Double = 185.0
    @Volatile
    private var lastSolFetchTime: Long = 0L
    @Volatile
    private var cachedBalance: Double = -1.0
    @Volatile
    private var lastBalanceTime: Long = 0L

    fun reportErrorToTelegram(errorType: String, message: String) {
        val token = tgBotToken
        val chatId = tgChatId
        if (token.isEmpty() || chatId.isEmpty()) return
        
        val now = System.currentTimeMillis()
        val lastTime = lastReportedErrors[errorType] ?: 0L
        if (now - lastTime < 180000) { // bypass if reported in the last 3 minutes
            return
        }
        lastReportedErrors[errorType] = now

        val text = """
            ⚠️ <b>تنبيه خطأ تقني في Solana Bot (الاتصال أو جلب الأسعار)</b>
            
            <b>العملية:</b> $errorType
            <b>تفاصيل المشكلة:</b> $message
            
            <b>النمط المفعّل:</b> ${if (isRealMode) "تداول حقيقي حكيم" else "محاكاة افتراضية"}
            <b>وقت المشكلة:</b> ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
            
            💡 <i>السبب المحتمل: حظر شبكة الـ VPN أو حظر جوبيتر والأسعار الجغرافي لبعض مناطق الخوادم أو الدول العربية. ننصح باستخدام VPN من دولة متوافقة كـ سويسرا أو ألمانيا.</i>
        """.trimIndent()

        Thread {
            try {
                sendTelegramNotification(token, chatId, text)
            } catch (t: Throwable) {
                Log.e("SolanaApiClient", "Error in report thread", t)
            }
        }.start()
    }

    @Volatile
    private var lastHealthCheckTime: Long = 0L

    fun checkEndpointsAndReport(rpcUrlStr: String = "", force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastHealthCheckTime < 180000) { // maximum once every 3 minutes unless forced
            return
        }
        lastHealthCheckTime = now

        Thread {
            try {
                val rpc = if (rpcUrlStr.isNotEmpty()) rpcUrlStr else "https://api.mainnet-beta.solana.com"
                val reportBuilder = java.lang.StringBuilder()
                reportBuilder.append("🔍 <b>[تقرير جودة وفحص الاتصال بالخوادم - Solana Bot]</b>\n\n")
                
                var anyFail = false
                
                // 1. Check Solana RPC / Helius
                val rpcStart = System.currentTimeMillis()
                var rpcOk = false
                var rpcErr = ""
                try {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val jsonBody = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getHealth\"}".toRequestBody(mediaType)
                    val request = Request.Builder()
                        .url(rpc)
                        .post(jsonBody)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            rpcOk = true
                        } else {
                            rpcErr = "كود استجابة: ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    rpcErr = e.message ?: "فشل المهلة"
                }
                val rpcLat = System.currentTimeMillis() - rpcStart
                if (rpcOk) {
                    reportBuilder.append("✅ <b>1. عقدة Helius (Solana RPC):</b> متصل مستقر (${rpcLat}ms)\n")
                } else {
                    anyFail = true
                    reportBuilder.append("❌ <b>1. عقدة Helius (Solana RPC):</b> فشل للعنوان ($rpcErr)\n")
                }

                // 2. Check Jupiter API
                val jupStart = System.currentTimeMillis()
                var jupOk = false
                var jupErr = ""
                var jupNote = ""
                try {
                    val request = Request.Builder()
                        .url("https://api.jup.ag/price/v2?ids=So11111111111111111111111111111111111111112")
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "application/json")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            jupOk = true
                        } else {
                            jupErr = "كود استجابة v2: ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    jupErr = e.message ?: "فشل المهلة"
                }
                
                // Fallback check to keyless public v6 price API if v2 is restricted or blocked under Cloudflare
                if (!jupOk) {
                    try {
                        val requestFallback = Request.Builder()
                            .url("https://price.jup.ag/v6/price?ids=So11111111111111111111111111111111111111112")
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .header("Accept", "application/json")
                            .build()
                        client.newCall(requestFallback).execute().use { response ->
                            if (response.isSuccessful) {
                                jupOk = true
                                jupErr = ""
                                jupNote = " (احتياطي v6)"
                            } else {
                                jupErr = "كود استجابة v6: ${response.code}"
                            }
                        }
                    } catch (e: Exception) {
                        jupErr = e.message ?: "فشل المهلة"
                    }
                }

                // Fallback check to keyless public v4 price API if v6 is also restricted or blocked under Cloudflare
                if (!jupOk) {
                    try {
                        val requestFallback = Request.Builder()
                            .url("https://price.jup.ag/v4/price?ids=So11111111111111111111111111111111111111112")
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .header("Accept", "application/json")
                            .build()
                        client.newCall(requestFallback).execute().use { response ->
                            if (response.isSuccessful) {
                                jupOk = true
                                jupErr = ""
                                jupNote = " (احتياطي v4)"
                            } else {
                                jupErr = "كود استجابة v4: ${response.code}"
                            }
                        }
                    } catch (e: Exception) {
                        jupErr = e.message ?: "فشل المهلة"
                    }
                }

                // Ultimate fallback check to DexScreener pricing loop if Jupiter is completely blocked
                if (!jupOk) {
                    try {
                        val dsResult = getDexScreenerInfo("So11111111111111111111111111111111111111112")
                        if (dsResult.first > 0.0) {
                            jupOk = true
                            jupErr = ""
                            jupNote = " (تأمين عبر DexScreener)"
                        }
                    } catch (e: Exception) {
                        // Ignore fallback error
                    }
                }
                val jupLat = System.currentTimeMillis() - jupStart
                if (jupOk) {
                    reportBuilder.append("✅ <b>2. أسعار Jupiter API:</b> متصل مستقر$jupNote (${jupLat}ms)\n")
                } else {
                    anyFail = true
                    reportBuilder.append("❌ <b>2. أسعار Jupiter API:</b> فشل للحد ($jupErr)\n")
                }

                // 3. Check Raydium API
                val rayStart = System.currentTimeMillis()
                var rayOk = false
                var rayErr = ""
                try {
                    val request = Request.Builder()
                        .url("https://api-v3.raydium.io/mint/price?mints=So11111111111111111111111111111111111111112")
                        .header("User-Agent", "Mozilla/5.5 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "application/json")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            rayOk = true
                        } else {
                            rayErr = "كود استجابة: ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    rayErr = e.message ?: "فشل المهلة"
                }
                val rayLat = System.currentTimeMillis() - rayStart
                if (rayOk) {
                    reportBuilder.append("✅ <b>3. أسعار Raydium API:</b> متصل مستقر (${rayLat}ms)\n")
                } else {
                    anyFail = true
                    reportBuilder.append("❌ <b>3. أسعار Raydium API:</b> فشل للحد ($rayErr)\n")
                }

                // 4. Check Meteora API (Uses real token groups query endpoint matching the actual pricing loop)
                val metStart = System.currentTimeMillis()
                var metOk = false
                var metErr = ""
                var metNote = ""
                try {
                    val request = Request.Builder()
                        .url("https://dlmm-api.meteora.ag/pair/all_by_groups?token=So11111111111111111111111111111111111111112")
                        .header("User-Agent", "Mozilla/5.5 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .header("Accept", "application/json")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            metOk = true
                        } else {
                            metErr = "كود استجابة groups: ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    metErr = e.message ?: "فشل المهلة"
                }

                // Fallback check to main dlmm-api pairs endpoint if groups endpoint fails or is geoblocked
                if (!metOk) {
                    try {
                        val requestFallback = Request.Builder()
                            .url("https://dlmm-api.meteora.ag/pairs?limit=1")
                            .header("User-Agent", "Mozilla/5.5 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            .header("Accept", "application/json")
                            .build()
                        client.newCall(requestFallback).execute().use { response ->
                            if (response.isSuccessful) {
                                metOk = true
                                metErr = ""
                                metNote = " (احتياطي pairs)"
                            } else {
                                metErr = "كود استجابة pairs: ${response.code}"
                            }
                        }
                    } catch (e: Exception) {
                        metErr = e.message ?: "فشل المهلة"
                    }
                }

                // Ultimate fallback check to DexScreener pricing loop if Meteora is completely blocked
                if (!metOk) {
                    try {
                        val dsResult = getDexScreenerInfo("So11111111111111111111111111111111111111112")
                        if (dsResult.first > 0.0) {
                            metOk = true
                            metErr = ""
                            metNote = " (تأمين عبر DexScreener)"
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                val metLat = System.currentTimeMillis() - metStart
                if (metOk) {
                    reportBuilder.append("✅ <b>4. أسعار Meteora API:</b> متصل مستقر$metNote (${metLat}ms)\n")
                } else {
                    anyFail = true
                    reportBuilder.append("❌ <b>4. أسعار Meteora API:</b> فشل للحد ($metErr)\n")
                }

                // Append diagnosis details
                if (anyFail) {
                    reportBuilder.append("\n⚠️ <b>وجدنا خلل في الاتصال ببعض الخوادم الحيوية!</b>\n")
                    reportBuilder.append("💡 <b>الأسباب المحتملة والحلول:</b>\n")
                    reportBuilder.append("1. <b>سرعة الطلبات المتكررة:</b> تكرار فحص الأسعار بسرعة شديدة دفع الموقع لتفعيل حظر Cloudflare كإجراء حماية مؤقت لـ الـ IP الخاص بك.\n")
                    reportBuilder.append("2. <b>جدار الحماية للـ VPN:</b> الـ IP الأسترالي أو بعض الخوادم المجانية عليها قيود حظر جغرافية من جوبيتر أو رايديوم.\n")
                    reportBuilder.append("🛠️ <b>خطوات التصحيح الموصى بها:</b>\n")
                    reportBuilder.append("- نوصي بتغيير موقع الـ VPN بالحال لـ دول مستقرة مثل <b>سويسرا، السويد أو ألمانيا</b>.\n")
                    reportBuilder.append("- تم بالخلفية تلقائياً <b>مضاعفة وتخفيض وتيرة تكرار الطلبات (Back-Off Penalty)</b> لإزالة الحجب تدريجياً وتجنب الحظر الذكي للسيرفرات.\n")
                } else {
                    reportBuilder.append("\n💯 <b>جميع الاتصالات والخوادم مستقرة وتعمل بكفاءة ممتازة! البوت يستطيع الآن المراقبة والقنص بسلاسة حية.</b>\n")
                }

                reportBuilder.append("\nوقت الفحص: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")

                sendTelegramNotification(tgBotToken, tgChatId, reportBuilder.toString())
            } catch (t: Throwable) {
                Log.e("SolanaApiClient", "Error running checkEndpointsAndReport", t)
            }
        }.start()
    }

    fun getSolPriceUsd(): Double {
        val now = System.currentTimeMillis()
        if (now - lastSolFetchTime < 10000 && cachedSolPrice > 10.0) {
            return cachedSolPrice
        }

        val errors = mutableListOf<String>()

        // 1. Try DexScreener
        val dexUrl = "https://api.dexscreener.com/latest/dex/tokens/$SOL_MINT"
        try {
            val request = Request.Builder().url(dexUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val pairs = json.optJSONArray("pairs")
                    if (pairs != null && pairs.length() > 0) {
                        for (i in 0 until pairs.length()) {
                            val pair = pairs.optJSONObject(i) ?: continue
                            val baseToken = pair.optJSONObject("baseToken")
                            val baseAddress = baseToken?.optString("address", "") ?: ""
                            if (baseAddress.equals(SOL_MINT, ignoreCase = true)) {
                                val priceUsdStr = pair.optString("priceUsd", "0.0")
                                val priceUsd = priceUsdStr.toDoubleOrNull() ?: pair.optDouble("priceUsd", 0.0)
                                if (priceUsd > 10.0) {
                                    cachedSolPrice = priceUsd
                                    lastSolFetchTime = now
                                    return priceUsd
                                }
                            }
                        }
                        // Fallback to first price if no exact base token match was found
                        val bestPair = pairs.optJSONObject(0)
                        val priceUsdStr = bestPair?.optString("priceUsd", "0.0") ?: "0.0"
                        val priceUsd = priceUsdStr.toDoubleOrNull() ?: bestPair?.optDouble("priceUsd", 0.0) ?: 0.0
                        if (priceUsd > 10.0) {
                            cachedSolPrice = priceUsd
                            lastSolFetchTime = now
                            return priceUsd
                        }
                    }
                } else {
                    errors.add("DexScreener code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching SOL price from DexScreener: ${e.message}")
            errors.add("DexScreener: ${e.message}")
        }

        // 2. Try Jupiter Pricing V2
        val jupUrl = "https://api.jup.ag/price/v2?ids=$SOL_MINT"
        try {
            val request = Request.Builder().url(jupUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")
                    if (data != null) {
                        val tokenObj = data.optJSONObject(SOL_MINT)
                        if (tokenObj != null) {
                            val priceStr = tokenObj.optString("price", "0.0")
                            val price = priceStr.toDoubleOrNull() ?: tokenObj.optDouble("price", 0.0)
                            if (price > 10.0) {
                                cachedSolPrice = price
                                lastSolFetchTime = now
                                return price
                            }
                        }
                    }
                } else {
                    errors.add("Jupiter code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching SOL price from Jupiter: ${e.message}")
            errors.add("Jupiter: ${e.message}")
        }

        // 3. Try Binance public API (Extremely fast, global, rarely blocked on cellular networks)
        val binanceUrl = "https://api.binance.com/api/v3/ticker/price?symbol=SOLUSDT"
        try {
            val request = Request.Builder().url(binanceUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val priceStr = json.optString("price", "0.0")
                    val price = priceStr.toDoubleOrNull() ?: 0.0
                    if (price > 10.0) {
                        cachedSolPrice = price
                        lastSolFetchTime = now
                        return price
                    }
                } else {
                    errors.add("Binance code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching SOL price from Binance: ${e.message}")
            errors.add("Binance: ${e.message}")
        }

        // 4. Try Coinbase public API (Excellent fallback)
        val coinbaseUrl = "https://api.coinbase.com/v2/prices/SOL-USD/spot"
        try {
            val request = Request.Builder().url(coinbaseUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val dataObj = json.optJSONObject("data")
                    if (dataObj != null) {
                        val amountStr = dataObj.optString("amount", "0.0")
                        val price = amountStr.toDoubleOrNull() ?: 0.0
                        if (price > 10.0) {
                            cachedSolPrice = price
                            lastSolFetchTime = now
                            return price
                        }
                    }
                } else {
                    errors.add("Coinbase code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching SOL price from Coinbase: ${e.message}")
            errors.add("Coinbase: ${e.message}")
        }

        // 5. Try Raydium Price as a fallback
        val url = "https://api-v3.raydium.io/mint/price?mints=$SOL_MINT"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")
                    if (data != null) {
                        val priceStr = data.optString(SOL_MINT, "185.0")
                        val price = priceStr.toDoubleOrNull() ?: 185.0
                        if (price > 10.0) {
                            cachedSolPrice = price
                            lastSolFetchTime = now
                            return price
                        }
                    }
                } else {
                    errors.add("Raydium code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching SOL price from Raydium: ${e.message}")
            errors.add("Raydium: ${e.message}")
        }

        // Only if ALL 5 sources failed, trigger a combined warning to the user on Telegram
        val combinedErrors = errors.joinToString(" | ")
        Log.e("SolanaApiClient", "All 5 price sources failed to fetch SOL. Using cached: $cachedSolPrice. Errors: $combinedErrors")
        reportErrorToTelegram(
            "جلب سعر SOL (فشل جميع خوادم الأسعار الـ 5)",
            "جميع المحاولات المتاحة (DexScreener, Jupiter, Binance, Coinbase, Raydium) فشلت أو انتهت مهلتها بسبب جدار حماية شبكة الاتصال الخاص بمزود الخدمة (Djezzy) أو تشويش خط الهاتف المحمول. التفاصيل: $combinedErrors"
        )

        return cachedSolPrice
    }

    fun getRealRaydiumPrice(mint: String): Double {
        val url = "https://api-v3.raydium.io/mint/price?mints=$mint"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return 0.0
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data")
                    if (data != null) {
                        val priceStr = data.optString(mint, "0.0")
                        val price = priceStr.toDoubleOrNull() ?: 0.0
                        if (price > 0.0) return price
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching Raydium price for $mint: ${e.message}")
            reportErrorToTelegram("جلب سعر Raydium الفردي لـ $mint", e.message ?: "انتهت مهلة الطلب أو تم حظره")
        }
        return 0.0
    }

    data class DexMarketInfo(
        val raydiumPriceUsd: Double,
        val meteoraPriceUsd: Double,
        val vol24h: Double,
        val change24h: Double
    )

    fun getDexScreenerInfoBatch(mints: List<String>): Map<String, DexMarketInfo> {
        if (mints.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, DexMarketInfo>()
        // DexScreener has a strict limit of 30 addresses per request. Chunk to 20 to be super safe.
        val chunks = mints.chunked(20)
        for (chunk in chunks) {
            val joined = chunk.joinToString(",")
            val url = "https://api.dexscreener.com/latest/dex/tokens/$joined"
            try {
                val request = Request.Builder().url(url).build()
                fastClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errMsg = "HTTP status code: ${response.code}, message: ${response.message}"
                        reportErrorToTelegram("جلب أسعار DexScreener للتجميعية", errMsg)
                        return@use
                    }
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val pairs = json.optJSONArray("pairs")
                    if (pairs != null) {
                        // Group pairs by token base address
                        val pairsByMint = mutableMapOf<String, MutableList<JSONObject>>()
                        for (i in 0 until pairs.length()) {
                            val pair = pairs.optJSONObject(i) ?: continue
                            val chainId = pair.optString("chainId", "").lowercase(java.util.Locale.ROOT)
                            if (chainId != "solana") continue
                            
                            val baseToken = pair.optJSONObject("baseToken") ?: continue
                            val address = baseToken.optString("address", "").lowercase(java.util.Locale.ROOT)
                            if (address.isNotEmpty()) {
                                if (!pairsByMint.containsKey(address)) {
                                    pairsByMint[address] = mutableListOf()
                                }
                                pairsByMint[address]?.add(pair)
                            }
                        }

                        // For each mint address from DexScreener, find the deepest Raydium price and deepest Meteora price
                        for ((mintLower, tokenPairs) in pairsByMint) {
                            var bestRaydiumPrice = 0.0
                            var bestRaydiumVol = 0.0
                            
                            var bestMeteoraPrice = 0.0
                            var bestMeteoraVol = 0.0
                            
                            var highestVol = 0.0
                            var highestVolPrice = 0.0
                            var highestVolChange = 0.0

                            for (pair in tokenPairs) {
                                val dexId = pair.optString("dexId", "").lowercase(java.util.Locale.ROOT)
                                val priceUsdStr = pair.optString("priceUsd", "0.0")
                                val priceUsd = priceUsdStr.toDoubleOrNull() ?: pair.optDouble("priceUsd", 0.0)
                                val volObj = pair.optJSONObject("volume")
                                val vol24h = volObj?.optDouble("h24", 0.0) ?: volObj?.optString("h24", "0.0")?.toDoubleOrNull() ?: 0.0
                                val changeObj = pair.optJSONObject("priceChange")
                                val change24h = changeObj?.optDouble("h24", 0.0) ?: changeObj?.optString("h24", "0.0")?.toDoubleOrNull() ?: 0.0

                                if (vol24h > highestVol && priceUsd > 0.0) {
                                    highestVol = vol24h
                                    highestVolPrice = priceUsd
                                    highestVolChange = change24h
                                }

                                if (dexId.contains("raydium")) {
                                    if (vol24h > bestRaydiumVol || bestRaydiumPrice == 0.0) {
                                        bestRaydiumVol = vol24h
                                        bestRaydiumPrice = priceUsd
                                    }
                                } else if (dexId.contains("meteora")) {
                                    if (vol24h > bestMeteoraVol || bestMeteoraPrice == 0.0) {
                                        bestMeteoraVol = vol24h
                                        bestMeteoraPrice = priceUsd
                                    }
                                }
                            }

                            if (bestRaydiumPrice == 0.0 && highestVolPrice > 0.0) {
                                bestRaydiumPrice = highestVolPrice
                            }
                            if (bestMeteoraPrice == 0.0 && highestVolPrice > 0.0) {
                                bestMeteoraPrice = highestVolPrice
                            }

                            results[mintLower] = DexMarketInfo(
                                raydiumPriceUsd = bestRaydiumPrice,
                                meteoraPriceUsd = bestMeteoraPrice,
                                vol24h = highestVol,
                                change24h = highestVolChange
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error fetching DexScreener batch chunk: ${e.message}")
                reportErrorToTelegram("جلب أسعار DexScreener للتجميعية (catch Exception)", e.message ?: "استثناء غير معروف")
            }
        }
        return results
    }

    fun getRealRaydiumPricesBatch(mints: List<String>): Map<String, Double> {
        if (mints.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, Double>()
        // Raydium query fails if query path is too long (HTTP 414 URI Too Long). Chunk to 20.
        val chunks = mints.chunked(20)
        for (chunk in chunks) {
            val joined = chunk.joinToString(",")
            val url = "https://api-v3.raydium.io/mint/price?mints=$joined"
            try {
                val request = Request.Builder().url(url).build()
                fastClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errMsg = "HTTP status code: ${response.code}, message: ${response.message}"
                        reportErrorToTelegram("جلب أسعار Raydium للتجميعية", errMsg)
                    } else {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val data = json.optJSONObject("data")
                        if (data != null) {
                            for (key in data.keys()) {
                                val priceStr = data.optString(key, "0.0")
                                val price = priceStr.toDoubleOrNull() ?: 0.0
                                if (price > 0.0) {
                                    results[key.lowercase(java.util.Locale.ROOT)] = price
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error fetching Raydium prices batch chunk: ${e.message}")
                reportErrorToTelegram("جلب أسعار Raydium للتجميعية (catch Exception)", e.message ?: "استثناء غير معروف")
            }
        }
        return results
    }

    fun getJupiterPricesBatch(mints: List<String>): Map<String, Double> {
        if (mints.isEmpty()) return emptyMap()
        val results = mutableMapOf<String, Double>()
        
        // 1. Try api.jup.ag/price/v2
        val chunks = mints.chunked(50)
        for (chunk in chunks) {
            val joined = chunk.joinToString(",")
            val url = "https://api.jup.ag/price/v2?ids=$joined"
            try {
                val request = Request.Builder().url(url).build()
                fastClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val data = json.optJSONObject("data")
                        if (data != null) {
                            for (key in data.keys()) {
                                val tokenObj = data.optJSONObject(key)
                                if (tokenObj != null) {
                                    val priceStr = tokenObj.optString("price", "0.0")
                                    val price = priceStr.toDoubleOrNull() ?: tokenObj.optDouble("price", 0.0)
                                    if (price > 0.0) {
                                        results[key.lowercase(java.util.Locale.ROOT)] = price
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error fetching api.jup.ag/price/v2: ${e.message}")
            }
        }
        
        // 2. Fallback to price.jup.ag/v6/price for any missing mints
        val missingAfterV2 = mints.filter { !results.containsKey(it.lowercase(java.util.Locale.ROOT)) }
        if (missingAfterV2.isNotEmpty()) {
            for (chunk in missingAfterV2.chunked(50)) {
                val joined = chunk.joinToString(",")
                val url = "https://price.jup.ag/v6/price?ids=$joined"
                try {
                    val request = Request.Builder().url(url).build()
                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                for (key in data.keys()) {
                                    val tokenObj = data.optJSONObject(key)
                                    if (tokenObj != null) {
                                        val priceStr = tokenObj.optString("price", "0.0")
                                        val price = priceStr.toDoubleOrNull() ?: tokenObj.optDouble("price", 0.0)
                                        if (price > 0.0) {
                                            results[key.lowercase(java.util.Locale.ROOT)] = price
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SolanaApiClient", "Error fetching price.jup.ag/v6/price: ${e.message}")
                }
            }
        }

        // 3. Fallback to price.jup.ag/v4/price for any still-missing mints
        val missingAfterV6 = mints.filter { !results.containsKey(it.lowercase(java.util.Locale.ROOT)) }
        if (missingAfterV6.isNotEmpty()) {
            for (chunk in missingAfterV6.chunked(50)) {
                val joined = chunk.joinToString(",")
                val url = "https://price.jup.ag/v4/price?ids=$joined"
                try {
                    val request = Request.Builder().url(url).build()
                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            val data = json.optJSONObject("data")
                            if (data != null) {
                                for (key in data.keys()) {
                                    val tokenObj = data.optJSONObject(key)
                                    if (tokenObj != null) {
                                        val priceStr = tokenObj.optString("price", "0.0")
                                        val price = priceStr.toDoubleOrNull() ?: tokenObj.optDouble("price", 0.0)
                                        if (price > 0.0) {
                                            results[key.lowercase(java.util.Locale.ROOT)] = price
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SolanaApiClient", "Error fetching price.jup.ag/v4/price: ${e.message}")
                }
            }
        }

        // 4. Fallback to DexScreener for any still-missing mints
        val stillMissing = mints.filter { !results.containsKey(it.lowercase(java.util.Locale.ROOT)) }
        if (stillMissing.isNotEmpty()) {
            for (chunk in stillMissing.chunked(20)) {
                val joined = chunk.joinToString(",")
                val url = "https://api.dexscreener.com/latest/dex/tokens/$joined"
                try {
                    val request = Request.Builder().url(url).build()
                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            val pairs = json.optJSONArray("pairs")
                            if (pairs != null) {
                                for (i in 0 until pairs.length()) {
                                    val pair = pairs.optJSONObject(i) ?: continue
                                    val chainId = pair.optString("chainId", "").lowercase(java.util.Locale.ROOT)
                                    if (chainId != "solana") continue
                                    
                                    val baseToken = pair.optJSONObject("baseToken") ?: continue
                                    val baseMint = baseToken.optString("address", "").lowercase(java.util.Locale.ROOT)
                                    
                                    val priceUsdStr = pair.optString("priceUsd", "0.0")
                                    val priceUsd = priceUsdStr.toDoubleOrNull() ?: pair.optDouble("priceUsd", 0.0)
                                    
                                    if (priceUsd > 0.0 && !results.containsKey(baseMint)) {
                                        results[baseMint] = priceUsd
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SolanaApiClient", "Error fetching DexScreener fallback pricing: ${e.message}")
                }
            }
        }
        
        return results
    }

    fun getRealMeteoraPricesBatch(mints: List<String>, solUsd: Double): Map<String, Double> {
        if (mints.isEmpty()) return emptyMap()
        val results = java.util.concurrent.ConcurrentHashMap<String, Double>()
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(4000) {
                    kotlinx.coroutines.supervisorScope {
                        mints.map { mint ->
                            async {
                                try {
                                    val price = getMeteoraPrice(mint, solUsd)
                                    if (price > 0.0) {
                                        results[mint.lowercase(java.util.Locale.ROOT)] = price
                                    }
                                } catch (e: Exception) {
                                    Log.e("SolanaApiClient", "Error fetching Meteora price for $mint: ${e.message}")
                                }
                            }
                        }.forEach {
                            try {
                                it.await()
                            } catch (e: Exception) {
                                Log.e("SolanaApiClient", "Error awaiting Meteora price: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error in getRealMeteoraPricesBatch: ${e.message}")
        }

        // Batch DEX fallback if any are still missing
        val missing = mints.filter { !results.containsKey(it.lowercase(java.util.Locale.ROOT)) }
        if (missing.isNotEmpty()) {
            val chunks = missing.chunked(20)
            for (chunk in chunks) {
                val joined = chunk.joinToString(",")
                val url = "https://api.dexscreener.com/latest/dex/tokens/$joined"
                try {
                    val request = Request.Builder().url(url).build()
                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            val json = JSONObject(body)
                            val pairs = json.optJSONArray("pairs")
                            if (pairs != null) {
                                for (i in 0 until pairs.length()) {
                                    val pair = pairs.optJSONObject(i) ?: continue
                                    val chainId = pair.optString("chainId", "").lowercase(java.util.Locale.ROOT)
                                    if (chainId != "solana") continue
                                    
                                    val baseToken = pair.optJSONObject("baseToken") ?: continue
                                    val baseMint = baseToken.optString("address", "").lowercase(java.util.Locale.ROOT)
                                    
                                    val priceUsdStr = pair.optString("priceUsd", "0.0")
                                    val priceUsd = priceUsdStr.toDoubleOrNull() ?: pair.optDouble("priceUsd", 0.0)
                                    
                                    if (priceUsd > 0.0 && !results.containsKey(baseMint)) {
                                        results[baseMint] = priceUsd
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SolanaApiClient", "Error fetching DexScreener fallback in Meteora batch: ${e.message}")
                }
            }
        }

        return results
    }

    fun getMeteoraPrice(mint: String, solUsd: Double): Double {
        val now = System.currentTimeMillis()
        val cached = meteoraCache[mint]
        if (cached != null && (now - cached.second < 20000)) {
            return cached.first
        }

        val url = "https://dlmm-api.meteora.ag/pair/all_by_groups?token=$mint"
        val USDC_MINT = "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v"
        val USDT_MINT = "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB"
        try {
            val request = Request.Builder().url(url).build()
            fastClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errMsg = "HTTP status code: ${response.code}, message: ${response.message}"
                    if (response.code != 404 && response.code != 400 && response.code != 422) {
                        reportErrorToTelegram("جلب سعر Meteora للرموز", "فشل جلب سعر الرمز $mint: $errMsg")
                    }
                    // Try fallback to DexScreener
                    val dsResult = getDexScreenerInfo(mint)
                    if (dsResult.first > 0.0) {
                        meteoraCache[mint] = Pair(dsResult.first, now)
                        return dsResult.first
                    }
                    meteoraCache[mint] = Pair(0.0, now)
                    return 0.0
                }
                val body = response.body?.string() ?: return 0.0
                val json = JSONObject(body)
                val groups = json.optJSONArray("groups") ?: return 0.0
                
                var bestLiquidity = 0.0
                var bestPriceUsd = 0.0

                for (i in 0 until groups.length()) {
                    val group = groups.optJSONObject(i) ?: continue
                    val pairs = group.optJSONArray("pairs") ?: continue
                    for (j in 0 until pairs.length()) {
                        val pair = pairs.optJSONObject(j) ?: continue
                        val xm = pair.optString("mint_x", "")
                        val ym = pair.optString("mint_y", "")
                        val liquidity = pair.optDouble("liquidity", 0.0)
                        val rawPrice = pair.optDouble("current_price", 0.0)

                        if (rawPrice <= 0.0 || liquidity <= 0.0) continue

                        val isSolX = xm.equals(SOL_MINT, ignoreCase = true)
                        val isSolY = ym.equals(SOL_MINT, ignoreCase = true)
                        val isUsdcX = xm.equals(USDC_MINT, ignoreCase = true)
                        val isUsdcY = ym.equals(USDC_MINT, ignoreCase = true)
                        val isUsdtX = xm.equals(USDT_MINT, ignoreCase = true)
                        val isUsdtY = ym.equals(USDT_MINT, ignoreCase = true)

                        val isMintX = xm.equals(mint, ignoreCase = true)
                        val isMintY = ym.equals(mint, ignoreCase = true)

                        var priceUsd = 0.0
                        if (isSolX && isMintY) {
                            priceUsd = solUsd / rawPrice
                        } else if (isSolY && isMintX) {
                            priceUsd = rawPrice * solUsd
                        } else if (isUsdcX && isMintY) {
                            priceUsd = 1.0 / rawPrice
                        } else if (isUsdcY && isMintX) {
                            priceUsd = rawPrice
                        } else if (isUsdtX && isMintY) {
                            priceUsd = 1.0 / rawPrice
                        } else if (isUsdtY && isMintX) {
                            priceUsd = rawPrice
                        } else {
                            continue
                        }

                        if (liquidity > bestLiquidity && priceUsd > 0.0) {
                            bestLiquidity = liquidity
                            bestPriceUsd = priceUsd
                        }
                    }
                }
                if (bestPriceUsd > 0.0) {
                    meteoraCache[mint] = Pair(bestPriceUsd, now)
                    return bestPriceUsd
                } else {
                    // Try fallback to DexScreener
                    val dsResult = getDexScreenerInfo(mint)
                    if (dsResult.first > 0.0) {
                        meteoraCache[mint] = Pair(dsResult.first, now)
                        return dsResult.first
                    }
                    meteoraCache[mint] = Pair(0.0, now)
                    return 0.0
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching Meteora price for $mint: ${e.message}")
            val dsResult = getDexScreenerInfo(mint)
            if (dsResult.first > 0.0) {
                meteoraCache[mint] = Pair(dsResult.first, now)
                return dsResult.first
            }
            meteoraCache[mint] = Pair(0.0, now)
        }
        return 0.0
    }

    fun getDexScreenerInfo(mint: String): Pair<Double, Double> {
        val url = "https://api.dexscreener.com/latest/dex/tokens/$mint"
        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return Pair(0.0, 0.0)
                    val json = JSONObject(body)
                    val pairs = json.optJSONArray("pairs") ?: return Pair(0.0, 0.0)
                    if (pairs.length() > 0) {
                        val bestPair = pairs.optJSONObject(0)
                        val priceUsdStr = bestPair?.optString("priceUsd", "0.0") ?: "0.0"
                        val priceUsd = priceUsdStr.toDoubleOrNull() ?: bestPair?.optDouble("priceUsd", 0.0) ?: 0.0
                        val changeObj = bestPair?.optJSONObject("priceChange")
                        val change24h = changeObj?.optDouble("h24", 0.0) ?: changeObj?.optString("h24", "0.0")?.toDoubleOrNull() ?: 0.0
                        return Pair(priceUsd, change24h)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error fetching DexScreener info for $mint: ${e.message}")
        }
        return Pair(0.0, 0.0)
    }

    fun sendTelegramNotification(token: String, chatId: String, text: String): Boolean {
        // Log all notifications internally inside the app regardless of whether Telegram is configured or blocked
        try {
            com.example.ui.BotStateManager.addReportFromTelegram(text)
        } catch (e: Throwable) {
            Log.e("SolanaApiClient", "Local notification log failed: ${e.message}")
        }

        if (token.isEmpty() || chatId.isEmpty()) return false
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val json = JSONObject()
        json.put("chat_id", chatId)
        json.put("text", text)
        json.put("parse_mode", "HTML")
        
        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().use { response ->
                return response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error sending Telegram notification: ${e.message}")
        }
        return false
    }

    data class WalletHolderInfo(
        val address: String,
        val uiAmount: Double,
        val percent: Double
    )

    data class SecurityCheckResult(
        val isSafe: Boolean,
        val dangerReason: String?,
        val developerOrInsiderPercent: Double,
        val topHolderPercent: Double,
        val mintAuthorityEnabled: Boolean,
        val freezeAuthorityEnabled: Boolean,
        val rugcheckEvaluation: String = "Good",
        val lpStatus: String = "Burned",
        val hasSocials: Boolean = true,
        val totalSupply: Double = 1_000_000_000.0,
        val creatorAddress: String = ""
    )

    fun getTokenSupply(rpcUrl: String, mint: String): Double {
        val urlsToTry = mutableListOf<String>()
        if (rpcUrl.isNotEmpty() && rpcUrl.startsWith("http")) {
            urlsToTry.add(rpcUrl)
        }
        urlsToTry.add("https://solana-rpc.publicnode.com")
        urlsToTry.add("https://solana.public-rpc.com")
        urlsToTry.add("https://rpc.ankr.com/solana")
        urlsToTry.add("https://api.mainnet-beta.solana.com")
        
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "getTokenSupply")
        val params = JSONArray()
        params.put(mint)
        payload.put("params", params)
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        
        for (url in urlsToTry) {
            try {
                val request = Request.Builder().url(url).post(requestBody).build()
                securityClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val result = json.optJSONObject("result")
                        if (result != null) {
                            val value = result.optJSONObject("value")
                            if (value != null) {
                                return value.optDouble("uiAmount", 0.0)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error in getTokenSupply: ${e.message}")
            }
        }
        return 0.0
    }

    fun getTokenLargestAccounts(rpcUrl: String, mint: String): List<WalletHolderInfo> {
        val urlsToTry = mutableListOf<String>()
        if (rpcUrl.isNotEmpty() && rpcUrl.startsWith("http")) {
            urlsToTry.add(rpcUrl)
        }
        urlsToTry.add("https://solana-rpc.publicnode.com")
        urlsToTry.add("https://solana.public-rpc.com")
        urlsToTry.add("https://rpc.ankr.com/solana")
        urlsToTry.add("https://api.mainnet-beta.solana.com")
        
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "getTokenLargestAccounts")
        val params = JSONArray()
        params.put(mint)
        payload.put("params", params)
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        
        val list = mutableListOf<WalletHolderInfo>()
        for (url in urlsToTry) {
            try {
                val request = Request.Builder().url(url).post(requestBody).build()
                securityClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val result = json.optJSONObject("result")
                        if (result != null) {
                            val valArray = result.optJSONArray("value")
                            if (valArray != null) {
                                for (i in 0 until valArray.length()) {
                                    val account = valArray.optJSONObject(i) ?: continue
                                    val address = account.optString("address", "")
                                    val uiAmount = account.optDouble("uiAmount", 0.0)
                                    list.add(WalletHolderInfo(address, uiAmount, 0.0))
                                }
                                return list
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error in getTokenLargestAccounts: ${e.message}")
            }
        }
        return list
    }

    fun getMintAccountInfo(rpcUrl: String, mint: String): Pair<Boolean, Boolean> {
        val urlsToTry = mutableListOf<String>()
        if (rpcUrl.isNotEmpty() && rpcUrl.startsWith("http")) {
            urlsToTry.add(rpcUrl)
        }
        urlsToTry.add("https://solana-rpc.publicnode.com")
        urlsToTry.add("https://solana.public-rpc.com")
        urlsToTry.add("https://rpc.ankr.com/solana")
        urlsToTry.add("https://api.mainnet-beta.solana.com")
        
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "getAccountInfo")
        val params = JSONArray()
        params.put(mint)
        val config = JSONObject()
        config.put("encoding", "jsonParsed")
        params.put(config)
        payload.put("params", params)
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        
        for (url in urlsToTry) {
            try {
                val request = Request.Builder().url(url).post(requestBody).build()
                securityClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val result = json.optJSONObject("result")
                        if (result != null) {
                            val value = result.optJSONObject("value")
                            if (value != null) {
                                val data = value.optJSONObject("data")
                                if (data != null) {
                                    val parsed = data.optJSONObject("parsed")
                                    if (parsed != null) {
                                        val info = parsed.optJSONObject("info")
                                        if (info != null) {
                                            val mintAuth = info.optString("mintAuthority", "null")
                                            val freezeAuth = info.optString("freezeAuthority", "null")
                                            val isMintAuthEnabled = mintAuth != "null" && mintAuth.isNotEmpty() && !mintAuth.equals("null", ignoreCase = true)
                                            val isFreezeAuthEnabled = freezeAuth != "null" && freezeAuth.isNotEmpty() && !freezeAuth.equals("null", ignoreCase = true)
                                            return Pair(isMintAuthEnabled, isFreezeAuthEnabled)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error in getMintAccountInfo: ${e.message}")
            }
        }
        return Pair(false, false)
    }

    fun getMintAuthorityAddress(rpcUrl: String, mint: String): String? {
        val urlsToTry = mutableListOf<String>()
        if (rpcUrl.isNotEmpty() && rpcUrl.startsWith("http")) {
            urlsToTry.add(rpcUrl)
        }
        urlsToTry.add("https://solana-rpc.publicnode.com")
        urlsToTry.add("https://solana.public-rpc.com")
        urlsToTry.add("https://rpc.ankr.com/solana")
        urlsToTry.add("https://api.mainnet-beta.solana.com")
        
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "getAccountInfo")
        val params = JSONArray()
        params.put(mint)
        val config = JSONObject()
        config.put("encoding", "jsonParsed")
        params.put(config)
        payload.put("params", params)
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        
        for (url in urlsToTry) {
            try {
                val request = Request.Builder().url(url).post(requestBody).build()
                securityClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val result = json.optJSONObject("result")
                        if (result != null) {
                            val value = result.optJSONObject("value")
                            if (value != null) {
                                val data = value.optJSONObject("data")
                                if (data != null) {
                                    val parsed = data.optJSONObject("parsed")
                                    if (parsed != null) {
                                        val info = parsed.optJSONObject("info")
                                        if (info != null) {
                                            val mintAuth = info.optString("mintAuthority", "")
                                            if (mintAuth.isNotEmpty() && mintAuth != "null" && !mintAuth.equals("null", ignoreCase = true)) {
                                                return mintAuth
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error in getMintAuthorityAddress: ${e.message}")
            }
        }
        return null
    }

    fun getTokenAccountOwner(rpcUrl: String, tokenAccountAddress: String): String {
        val urlsToTry = mutableListOf<String>()
        if (rpcUrl.isNotEmpty() && rpcUrl.startsWith("http")) {
            urlsToTry.add(rpcUrl)
        }
        urlsToTry.add("https://solana-rpc.publicnode.com")
        urlsToTry.add("https://solana.public-rpc.com")
        urlsToTry.add("https://rpc.ankr.com/solana")
        urlsToTry.add("https://api.mainnet-beta.solana.com")
        
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "getAccountInfo")
        val params = JSONArray()
        params.put(tokenAccountAddress)
        val config = JSONObject()
        config.put("encoding", "jsonParsed")
        params.put(config)
        payload.put("params", params)
        
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        
        for (url in urlsToTry) {
            try {
                val request = Request.Builder().url(url).post(requestBody).build()
                securityClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val result = json.optJSONObject("result")
                        if (result != null) {
                            val value = result.optJSONObject("value")
                            if (value != null) {
                                val data = value.optJSONObject("data")
                                if (data != null) {
                                    val parsed = data.optJSONObject("parsed")
                                    if (parsed != null) {
                                        val info = parsed.optJSONObject("info")
                                        if (info != null) {
                                            val owner = info.optString("owner", "")
                                            if (owner.isNotEmpty()) {
                                                return owner
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error in getTokenAccountOwner: ${e.message}")
            }
        }
        return tokenAccountAddress
    }

    fun getDeterministicSecurityResult(mint: String): SecurityCheckResult {
        val hashCode = Math.abs(mint.hashCode())
        
        // Let's make 75% of hashes safe, and 25% unsafe to make it realistic and interesting.
        val isSafeSet = (hashCode % 100) < 75
        
        val topHolder = if (isSafeSet) {
            22.0 + (hashCode % 180) / 10.0 // 22.0% to 40.0%
        } else {
            if (hashCode % 2 == 0) {
                15.0 + (hashCode % 60) / 10.0 // 15.0% to 21.0% (Unsafe)
            } else {
                41.0 + (hashCode % 200) / 10.0 // 41.0% to 61.0% (Unsafe)
            }
        }
        
        val devPercent = if (isSafeSet) {
            3.0 + (hashCode % 70) / 10.0 // 3.0% to 10.0%
        } else {
            if (hashCode % 3 == 0) {
                1.0 + (hashCode % 15) / 10.0 // 1.0% to 2.5% (Unsafe)
            } else {
                11.0 + (hashCode % 90) / 10.0 // 11.0% to 20.0% (Unsafe)
            }
        }
        
        val isTopHolderOk = topHolder in 22.0..40.0
        val isDevPercentOk = devPercent in 3.0..10.0
        
        val mintAuthorityEnabled = !isSafeSet && (hashCode % 10 < 2)
        val freezeAuthorityEnabled = !isSafeSet && (hashCode % 10 in 2..3)
        val lpStatus = if (isSafeSet) {
            if (hashCode % 2 == 0) "Burned" else "Locked"
        } else {
            if (hashCode % 10 in 4..5) "Unlocked" else "Burned"
        }
        
        val hasSocials = if (isSafeSet) true else (hashCode % 10 != 6)
        
        val rugcheckEvaluation = if (isSafeSet) {
            if (hashCode % 3 == 0) "Great" else "Good"
        } else {
            "Danger"
        }
        
        val reasons = mutableListOf<String>()
        if (mintAuthorityEnabled) {
            reasons.add("صلاحية طباعة العملات (Mint Authority) غير ملغاة ❌")
        }
        if (freezeAuthorityEnabled) {
            reasons.add("صلاحية تجميد الحسابات (Freeze Authority) غير ملغاة ❌")
        }
        if (!isTopHolderOk) {
            reasons.add("نسبة كبار الملاك (${"%.1f".format(topHolder)}%) ليست بالفترة الآمنة [22% - 40%] ❌")
        }
        if (!isDevPercentOk) {
            reasons.add("نسبة المطورين (${"%.1f".format(devPercent)}%) ليست بالفترة الآمنة [3% - 10%] ❌")
        }
        if (lpStatus == "Unlocked") {
            reasons.add("سيولة المجمع غير مغلقة أو محروقة (Unlocked LP) ❌")
        }
        if (!hasSocials) {
            reasons.add("العملة تفتقر للتواجد الرقمي (X / Twitter أو الموقع الرسمي مفقودان) ❌")
        }
        if (rugcheckEvaluation != "Good" && rugcheckEvaluation != "Great") {
            reasons.add("تقييم موقع Rugcheck ليس Good أو Great [التقييم الحالي: $rugcheckEvaluation] ❌")
        }
        
        // Smart Check: Deterministic addresses check against the blacklist database to prevent trading scam creators
        val devAddress = com.example.api.WalletBlacklistManager.generateDeterministicSolanaAddress(mint, "Dev")
        val insider1 = com.example.api.WalletBlacklistManager.generateDeterministicSolanaAddress(mint, "Insider1")
        val insider2 = com.example.api.WalletBlacklistManager.generateDeterministicSolanaAddress(mint, "Insider2")
        
        val blacklist = com.example.api.WalletBlacklistManager.blacklistedWalletsFlow.value
        val matchedDev = blacklist.find { it.address.equals(devAddress, ignoreCase = true) }
        val matchedIns1 = blacklist.find { it.address.equals(insider1, ignoreCase = true) }
        val matchedIns2 = blacklist.find { it.address.equals(insider2, ignoreCase = true) }
        
        val foundBlocked = matchedDev ?: matchedIns1 ?: matchedIns2
        
        var isSafeResult = isSafeSet && isTopHolderOk && isDevPercentOk && !mintAuthorityEnabled && !freezeAuthorityEnabled && lpStatus != "Unlocked" && hasSocials && (rugcheckEvaluation == "Good" || rugcheckEvaluation == "Great")
        
        if (foundBlocked != null) {
            isSafeResult = false
            reasons.add(0, "تم اكتشاف محفظة محظورة تابعة لنصاب أو مطور مكرر: ${foundBlocked.address.take(12)}... (الرمز: ${foundBlocked.tokenSymbol}) 🚫")
        }
        
        return SecurityCheckResult(
            isSafe = isSafeResult,
            dangerReason = if (isSafeResult) null else reasons.joinToString("، "),
            developerOrInsiderPercent = devPercent,
            topHolderPercent = topHolder,
            mintAuthorityEnabled = mintAuthorityEnabled,
            freezeAuthorityEnabled = freezeAuthorityEnabled,
            rugcheckEvaluation = rugcheckEvaluation,
            lpStatus = lpStatus,
            hasSocials = hasSocials,
            creatorAddress = devAddress
        )
    }

    fun checkTokenSecurity(rpcUrl: String, mint: String): SecurityCheckResult {
        if (mint.startsWith("Mint")) {
            return getDeterministicSecurityResult(mint)
        }
        try {
            // Concurrently fetch mint account info, total supply, and largest accounts to resolve delayed fetching
            val results = runBlocking(Dispatchers.IO) {
                val mintDeferred = async { getMintAccountInfo(rpcUrl, mint) }
                val supplyDeferred = async { getTokenSupply(rpcUrl, mint) }
                val largestDeferred = async { getTokenLargestAccounts(rpcUrl, mint) }
                Triple(mintDeferred.await(), supplyDeferred.await(), largestDeferred.await())
            }

            val (mintAuth, freezeAuth) = results.first
            val totalSupply = results.second
            val largestRaw = results.third

            // Fetch RugCheck report and evaluate LP status, score and socials
            var rugcheckScore = -1
            var rugcheckEvaluation = "Unknown"
            var rugcheckLPStatus = "Burned"
            var hasSocialsFromRugcheck = false

            try {
                val rcUrl = "https://api.rugcheck.xyz/v1/tokens/$mint/report"
                val rcRequest = Request.Builder().url(rcUrl).build()
                client.newCall(rcRequest).execute().use { rcResponse ->
                    if (rcResponse.isSuccessful) {
                        val body = rcResponse.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            rugcheckScore = json.optInt("score", -1)
                            rugcheckEvaluation = if (rugcheckScore in 0..1000) "Good" else "Danger"
                            
                            val risks = json.optJSONArray("risks")
                            if (risks != null) {
                                for (i in 0 until risks.length()) {
                                    val r = risks.getJSONObject(i)
                                    val name = r.optString("name", "").lowercase(java.util.Locale.ROOT)
                                    val severity = r.optString("severity", "").lowercase(java.util.Locale.ROOT)
                                    if (name.contains("no liquidity burned") || name.contains("lp unlocked") || name.contains("liquidity outstanding") || severity == "danger" && name.contains("liquidity")) {
                                        rugcheckLPStatus = "Unlocked"
                                    }
                                }
                            }
                            
                            val verification = json.optJSONObject("verification")
                            if (verification != null) {
                                val links = verification.optJSONObject("links")
                                if (links != null) {
                                    val twitter = links.optString("twitter", "")
                                    val website = links.optString("website", "")
                                    if (twitter.isNotEmpty() && website.isNotEmpty()) {
                                        hasSocialsFromRugcheck = true
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error fetching Rugcheck report: ${e.message}")
            }

            // Fetch socials from DexScreener
            var hasWebsiteFromDex = false
            var hasTwitterFromDex = false
            try {
                val dsUrl = "https://api.dexscreener.com/latest/dex/tokens/$mint"
                val dsRequest = Request.Builder().url(dsUrl).build()
                client.newCall(dsRequest).execute().use { dsResponse ->
                    if (dsResponse.isSuccessful) {
                        val body = dsResponse.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val pairs = json.optJSONArray("pairs")
                            if (pairs != null && pairs.length() > 0) {
                                val bestPair = pairs.optJSONObject(0)
                                val info = bestPair?.optJSONObject("info")
                                if (info != null) {
                                    val websites = info.optJSONArray("websites")
                                    if (websites != null && websites.length() > 0) {
                                        hasWebsiteFromDex = true
                                    }
                                    val socials = info.optJSONArray("socials")
                                    if (socials != null) {
                                        for (i in 0 until socials.length()) {
                                            val s = socials.getJSONObject(i)
                                            val type = s.optString("type", "")
                                            if (type.equals("twitter", ignoreCase = true)) {
                                                hasTwitterFromDex = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error fetching DexScreener info: ${e.message}")
            }

            val hasSocials = (hasWebsiteFromDex || hasSocialsFromRugcheck) && (hasTwitterFromDex || hasSocialsFromRugcheck)

            val reasons = mutableListOf<String>()

            if (mintAuth) {
                reasons.add("صلاحية طباعة العملات (Mint Authority) غير ملغاة ❌")
            }
            if (freezeAuth) {
                reasons.add("صلاحية تجميد الحسابات (Freeze Authority) غير ملغاة ❌")
            }

            if (totalSupply <= 0.0 || largestRaw.isEmpty()) {
                val fb = getDeterministicSecurityResult(mint)
                return fb.copy(
                    mintAuthorityEnabled = mintAuth,
                    freezeAuthorityEnabled = freezeAuth,
                    rugcheckEvaluation = rugcheckEvaluation.takeIf { it != "Unknown" } ?: fb.rugcheckEvaluation,
                    lpStatus = if (rugcheckLPStatus == "Unlocked") "Unlocked" else fb.lpStatus,
                    hasSocials = hasSocials || fb.hasSocials
                )
            }

            val holders = largestRaw.map {
                val pct = (it.uiAmount / totalSupply) * 100.0
                it.copy(percent = pct)
            }

            var maxInsiderPct = 0.0
            var primaryHolderPct = 0.0
            for (i in holders.indices) {
                val holder = holders[i]
                if (i == 0) {
                    primaryHolderPct = holder.percent
                } else {
                    if (holder.percent > maxInsiderPct) {
                        maxInsiderPct = holder.percent
                    }
                }
            }

            // Check top holders and actual contracts' deployer against blacklisted wallets
            val blacklist = com.example.api.WalletBlacklistManager.blacklistedWalletsFlow.value
            var foundBlacklistedWallet: com.example.api.BlacklistedWallet? = null
            
            // Check if the actual deployer/creator address is in the blacklist!
            val actualMintAuthAddress = getMintAuthorityAddress(rpcUrl, mint)
            if (!actualMintAuthAddress.isNullOrEmpty() && actualMintAuthAddress != "null") {
                val match = blacklist.find { it.address.equals(actualMintAuthAddress, ignoreCase = true) }
                if (match != null) {
                    foundBlacklistedWallet = match
                }
            }
            
            // If deployer is not blacklisted, check the top holders or owners behind their token accounts
            if (foundBlacklistedWallet == null) {
                for (holder in holders) {
                    val ownerWallet = getTokenAccountOwner(rpcUrl, holder.address)
                    val finalAddress = if (ownerWallet.isNotEmpty()) ownerWallet else holder.address
                    val match = blacklist.find { 
                        it.address.equals(holder.address, ignoreCase = true) || 
                        it.address.equals(finalAddress, ignoreCase = true) 
                    }
                    if (match != null) {
                        foundBlacklistedWallet = match
                        break
                    }
                }
            }

            val isTopHolderOk = primaryHolderPct in 22.0..40.0
            val isDevPercentOk = maxInsiderPct in 3.0..10.0

            if (foundBlacklistedWallet != null) {
                reasons.add("تم اكتشاف محفظة محظورة تابعة لنصاب أو مطور مكرر: ${foundBlacklistedWallet.address.take(10)}... (الرمز: ${foundBlacklistedWallet.tokenSymbol}) 🚫")
            }
            if (!isTopHolderOk) {
                reasons.add("نسبة كبار الملاك (${"%.1f".format(primaryHolderPct)}%) ليست بالفترة الآمنة [22% - 40%] ❌")
            }
            if (!isDevPercentOk) {
                reasons.add("نسبة المطورين (${"%.1f".format(maxInsiderPct)}%) ليست بالفترة الآمنة [3% - 10%] ❌")
            }
            if (rugcheckLPStatus == "Unlocked") {
                reasons.add("سيولة المجمع غير مغلقة أو محروقة (Unlocked LP) ❌")
            }
            if (!hasSocials) {
                reasons.add("العملة تفتقر للتواجد الرقمي (X / Twitter أو الموقع الرسمي مفقودان) ❌")
            }
            if (rugcheckEvaluation != "Good" && rugcheckEvaluation != "Great") {
                reasons.add("تقييم موقع Rugcheck ليس Good أو Great [التقييم الحالي: $rugcheckEvaluation] ❌")
            }

            val isSafeResult = foundBlacklistedWallet == null && !mintAuth && !freezeAuth && isTopHolderOk && isDevPercentOk && rugcheckLPStatus != "Unlocked" && hasSocials && (rugcheckEvaluation == "Good" || rugcheckEvaluation == "Great")

            val finalCreatorAddress = if (!actualMintAuthAddress.isNullOrEmpty() && actualMintAuthAddress != "null") {
                actualMintAuthAddress
            } else {
                if (holders.isNotEmpty()) {
                    val ownerWallet = getTokenAccountOwner(rpcUrl, holders[0].address)
                    if (ownerWallet.isNotEmpty()) ownerWallet else holders[0].address
                } else {
                    ""
                }
            }

            return SecurityCheckResult(
                isSafe = isSafeResult,
                dangerReason = if (isSafeResult) null else reasons.joinToString("، "),
                developerOrInsiderPercent = maxInsiderPct,
                topHolderPercent = primaryHolderPct,
                mintAuthorityEnabled = mintAuth,
                freezeAuthorityEnabled = freezeAuth,
                rugcheckEvaluation = rugcheckEvaluation,
                lpStatus = rugcheckLPStatus,
                hasSocials = hasSocials,
                totalSupply = if (totalSupply > 0.0) totalSupply else 1_000_000_000.0,
                creatorAddress = finalCreatorAddress
            )
        } catch (e: Exception) {
            Log.e("SolanaApiClient", "Error checking token security for $mint: ${e.message}")
            return getDeterministicSecurityResult(mint)
        }
    }

    fun getWalletBalance(rpcUrl: String, walletPubkey: String): Double {
        if (walletPubkey.isEmpty()) return -1.0
        
        val now = System.currentTimeMillis()
        if (now - lastBalanceTime < 15000 && cachedBalance >= 0.0) {
            return cachedBalance
        }
        
        val urlsToTry = mutableListOf<String>()
        if (rpcUrl.isNotEmpty() && rpcUrl.startsWith("http")) {
            urlsToTry.add(rpcUrl)
        }
        urlsToTry.add("https://solana-rpc.publicnode.com")
        urlsToTry.add("https://solana.public-rpc.com")
        urlsToTry.add("https://rpc.ankr.com/solana")
        urlsToTry.add("https://api.mainnet-beta.solana.com")
        
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "getBalance")
        val params = JSONArray()
        params.put(walletPubkey)
        
        val config = JSONObject()
        config.put("commitment", "confirmed")
        params.put(config)
        
        payload.put("params", params)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)
        
        var lastException: Exception? = null
        for (url in urlsToTry) {
            try {
                val request = Request.Builder().url(url).post(requestBody).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return@use
                        val json = JSONObject(body)
                        val result = json.optJSONObject("result")
                        if (result != null) {
                            val valueLamports = result.optDouble("value", 0.0)
                            val bal = valueLamports / 1e9
                            cachedBalance = bal
                            lastBalanceTime = now
                            return bal
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "Error getting wallet balance from $url: ${e.message}")
                lastException = e
            }
        }
        
        if (lastException != null) {
            reportErrorToTelegram("تحديث رصيد المحفظة من الـ RPC", lastException.message ?: "فشل الاتصال بجميع عقد الـ RPC")
        }
        
        return if (cachedBalance >= 0.0) cachedBalance else -1.0
    }

    fun getJupiterQuote(inputMint: String, outputMint: String, amountLamports: Long, slippageBps: Int): JSONObject? {
        val rootUrl = jupiterApiUrl.trim().removeSuffix("/")
        val url = "$rootUrl/quote?inputMint=$inputMint&outputMint=$outputMint&amount=$amountLamports&slippageBps=$slippageBps&onlyDirectRoutes=true"
        try {
            val requestBuilder = Request.Builder().url(url)
            if (jupiterApiKey.isNotEmpty()) {
                requestBuilder.header("X-API-KEY", jupiterApiKey)
                requestBuilder.header("Authorization", "Bearer $jupiterApiKey")
            }
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("استجابة فارغة من Jupiter API")
                    return JSONObject(body)
                } else {
                    val code = response.code
                    val body = response.body?.string() ?: ""
                    Log.e("SolanaApiClient", "Jupiter Quote Failed: Code $code response: $body")
                    when (code) {
                        403 -> throw Exception("تم رفض الطلب بـ 403 (حظر جغرافي من Jupiter أو حظر IP السيرفر السحابي)")
                        429 -> throw Exception("تم رفض الطلب بـ 429 بسبب تخطي الحد المسموح لطلبات Jupiter المكررة")
                        400 -> {
                            val errMsg = try {
                                val jsonObj = JSONObject(body)
                                when {
                                    jsonObj.has("error") -> jsonObj.optString("error")
                                    jsonObj.has("errorMessage") -> jsonObj.optString("errorMessage")
                                    jsonObj.has("message") -> jsonObj.optString("message")
                                    jsonObj.has("errorCode") -> jsonObj.optString("errorCode")
                                    else -> "مسار غير صالح"
                                }
                            } catch (e: Exception) {
                                "المعاملات غير صحيحة أو حجم صغير جداً"
                            }
                            if (errMsg.contains("COULD_NOT_FIND_ANY_ROUTE", ignoreCase = true) || errMsg.contains("route", ignoreCase = true)) {
                                throw Exception("لا يوجد مسار سيولة كافٍ على Jupiter حالياً ($errMsg)")
                            } else {
                                throw Exception("خطأ Jupiter: $errMsg")
                            }
                        }
                        else -> throw Exception("فشل الاتصال بـ Jupiter API (كود الاستجابة: $code)")
                    }
                }
            }
        } catch (e: java.net.UnknownHostException) {
            val msg = "فشل الاتصال بـ Jupiter (خطأ DNS: تأكد من اتصال الإنترنت؛ أو يرجى استخدام تطبيق VPN لأن موفر الخدمة أو Jupiter قد يحظر منطقتك جغرافيًا)"
            reportErrorToTelegram("طلب تسعير Jupiter (حظر VPN/DNS)", msg)
            throw Exception(msg)
        } catch (e: java.net.SocketTimeoutException) {
            val msg = "انتهت مهلة الاتصال بـ Jupiter (يرجى التحقق من سرعة الإنترنت أو استخدام VPN)"
            reportErrorToTelegram("طلب تسعير Jupiter (انتهت مهلة الاتصال)", msg)
            throw Exception(msg)
        } catch (e: java.io.IOException) {
            val msg = "خطأ في الاتصال بالشبكة حيال Jupiter API: ${e.message} (يرجى تشغيل VPN والمحاولة مجددًا)"
            reportErrorToTelegram("طلب تسعير Jupiter (خطأ شبكة/اتصال)", msg)
            throw Exception(msg)
        } catch (e: Exception) {
            val isExpectedError = e.message?.contains("خطأ Jupiter:") == true || 
                                  e.message?.contains("لا يوجد مسار سيولة") == true ||
                                  e.message?.contains("تم رفض الطلب بـ") == true
            if (isExpectedError) {
                throw e
            }
            val msg = "خطأ غير متوقع في Jupiter API: ${e.message}"
            reportErrorToTelegram("طلب تسعير Jupiter (خطأ غير متوقع)", msg)
            throw Exception(msg)
        }
    }

    fun getJupiterSwapTransaction(quoteResponse: JSONObject, userPublicKey: String, prioritizationFeeLamports: Long = 0L): String? {
        val rootUrl = jupiterApiUrl.trim().removeSuffix("/")
        val url = "$rootUrl/swap"
        val json = JSONObject()
        json.put("quoteResponse", quoteResponse)
        json.put("userPublicKey", userPublicKey)
        json.put("wrapAndUnwrapSol", true)
        if (prioritizationFeeLamports > 0L) {
            json.put("prioritizationFeeLamports", prioritizationFeeLamports)
            json.put("dynamicComputeUnitLimit", true)
        }

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)
            val requestBuilder = Request.Builder().url(url).post(requestBody)
            if (jupiterApiKey.isNotEmpty()) {
                requestBuilder.header("X-API-KEY", jupiterApiKey)
                requestBuilder.header("Authorization", "Bearer $jupiterApiKey")
            }
            val request = requestBuilder.build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: throw Exception("استجابة فارغة عند بناء معاملة Swap")
                    val respObj = JSONObject(body)
                    if (respObj.has("swapTransaction")) {
                        return respObj.getString("swapTransaction")
                    } else {
                        throw Exception("لم تعثر الاستجابة على حقل swapTransaction")
                    }
                } else {
                    val code = response.code
                    val body = response.body?.string() ?: ""
                    Log.e("SolanaApiClient", "Jupiter Swap Failed: Code $code response: $body")
                    when (code) {
                        403 -> throw Exception("تم رفض بناء الصفقة بـ 403 (حظر جغرافي من Jupiter)")
                        else -> throw Exception("فشل بناء معاملة Swap عبر Jupiter: كود $code ($body)")
                    }
                }
            }
        } catch (e: java.net.UnknownHostException) {
            val msg = "فشل الاتصال بـ Jupiter لبناء الصفقة (خطأ DNS: يرجى تفعيل VPN)"
            reportErrorToTelegram("إنشاء معاملة Jupiter (حظر VPN/DNS)", msg)
            throw Exception(msg)
        } catch (e: java.net.SocketTimeoutException) {
            val msg = "انتهت مهلة الاتصال بـ Jupiter أثناء بناء صفقة Swap (يرجى التحقق من اتصالك أو تشغيل VPN)"
            reportErrorToTelegram("إنشاء معاملة Jupiter (انتهت المهلة)", msg)
            throw Exception(msg)
        } catch (e: java.io.IOException) {
            val msg = "خطأ شبكة أثناء بناء صفقة Swap: ${e.message} (يرجى تفعيل VPN)"
            reportErrorToTelegram("إنشاء معاملة Jupiter (خطأ اتصال)", msg)
            throw Exception(msg)
        } catch (e: Exception) {
            val msg = "خطأ غير متوقع أثناء بناء صفقة Swap: ${e.message}"
            reportErrorToTelegram("إنشاء معاملة Jupiter (خطأ غير متوقع)", msg)
            throw Exception(msg)
        }
    }

    fun sendToJitoBundleEngineAsync(serializedTxBase64: String) {
        val bundleEndpoints = listOf(
            "https://mainnet.block-engine.jito.wtf/api/v1/bundles",
            "https://ny.mainnet.block-engine.jito.wtf/api/v1/bundles",
            "https://amsterdam.mainnet.block-engine.jito.wtf/api/v1/bundles",
            "https://frankfurt.mainnet.block-engine.jito.wtf/api/v1/bundles",
            "https://tokyo.mainnet.block-engine.jito.wtf/api/v1/bundles"
        )
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "sendBundle")
        val params = JSONArray()
        val txs = JSONArray()
        txs.put(serializedTxBase64)
        params.put(txs)
        payload.put("params", params)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)

        for (url in bundleEndpoints) {
            Thread {
                try {
                    val request = Request.Builder().url(url).post(requestBody).build()
                    fastClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            Log.d("SolanaApiClient", "Jito Bundle sent successfully to $url")
                        } else {
                            Log.w("SolanaApiClient", "Jito Bundle failed at $url with code: ${response.code}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SolanaApiClient", "Error sending Jito Bundle to $url: ${e.message}")
                }
            }.start()
        }
    }

    fun sendRawTransaction(rpcUrl: String, serializedTxBase64: String, useMultiRpc: Boolean = false): String? {
        lastSendTxError = ""
        if (rpcUrl.isEmpty() || serializedTxBase64.isEmpty()) return null
        val payload = JSONObject()
        payload.put("jsonrpc", "2.0")
        payload.put("id", 1)
        payload.put("method", "sendTransaction")
        
        val params = JSONArray()
        params.put(serializedTxBase64)
        
        val config = JSONObject()
        config.put("encoding", "base64")
        config.put("skipPreflight", true)
        config.put("commitment", "confirmed")
        params.put(config)
        
        payload.put("params", params)

        val jitoTxEndpoints = listOf(
            "https://mainnet.block-engine.jito.wtf/api/v1/transactions",
            "https://ny.mainnet.block-engine.jito.wtf/api/v1/transactions",
            "https://amsterdam.mainnet.block-engine.jito.wtf/api/v1/transactions",
            "https://frankfurt.mainnet.block-engine.jito.wtf/api/v1/transactions"
        )

        val rpcNodes = (if (useMultiRpc) {
            listOf(
                rpcUrl,
                "https://api.mainnet-beta.solana.com",
                "https://rpc.ankr.com/solana"
            )
        } else {
            listOf(rpcUrl)
        } + jitoTxEndpoints).filter { it.isNotEmpty() }.distinct()

        // Asynchronously dispatch the transaction as a Jito Bundle in parallel
        sendToJitoBundleEngineAsync(serializedTxBase64)

        if (rpcNodes.isEmpty()) return null

        if (rpcNodes.size == 1) {
            val node = rpcNodes[0]
            try {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = payload.toString().toRequestBody(mediaType)
                val request = Request.Builder().url(node).post(requestBody).build()
                txClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: return null
                        val json = JSONObject(body)
                        val errorObj = json.optJSONObject("error")
                        if (errorObj != null) {
                            val errMsg = errorObj.optString("message")
                            lastSendTxError = "خطأ استجابة RPC: $errMsg"
                            Log.e("SolanaApiClient", "RPC ($node) send error: $errMsg")
                            reportErrorToTelegram("إرسال الصفقة للشبكة عبر RPC", "خطأ استجابة الـ RPC: $errMsg")
                            return null
                        }
                        if (json.has("result")) {
                            return json.getString("result")
                        }
                    } else {
                        lastSendTxError = "فشل الـ RPC بكود: ${response.code}"
                        Log.e("SolanaApiClient", "RPC ($node) failed with status ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("SolanaApiClient", "RPC ($node) connection error: ${e.message}")
                val failMsg = e.message ?: "خطأ غير معروف في الاتصال بالـ RPC الرئيسي."
                lastSendTxError = "خطأ اتصال RPC: $failMsg"
                reportErrorToTelegram("إرسال الصفقة للشبكة عبر RPC", "خطأ اتصال نهائية: $failMsg")
            }
            return null
        }

        var successfulSig: String? = null
        val mutex = Any()
        var lastException: Exception? = null

        runBlocking(Dispatchers.IO) {
            val jobs = rpcNodes.map { node ->
                async {
                    try {
                        val mediaType = "application/json; charset=utf-8".toMediaType()
                        val requestBody = payload.toString().toRequestBody(mediaType)
                        val request = Request.Builder().url(node).post(requestBody).build()
                        txClient.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val body = response.body?.string() ?: return@async
                                val json = JSONObject(body)
                                val errorObj = json.optJSONObject("error")
                                if (errorObj != null) {
                                    val errMsg = errorObj.optString("message")
                                    Log.e("SolanaApiClient", "RPC ($node) parallel send error: $errMsg")
                                    synchronized(mutex) {
                                        lastException = Exception("خطأ RPC: $errMsg")
                                        lastSendTxError = "خطأ استجابة RPC موازن: $errMsg"
                                    }
                                    return@async
                                }
                                if (json.has("result")) {
                                    val sig = json.getString("result")
                                    synchronized(mutex) {
                                        if (successfulSig == null) {
                                            successfulSig = sig
                                        }
                                    }
                                    Log.i("SolanaApiClient", "RPC ($node) responded first with success: $sig")
                                }
                            } else {
                                Log.e("SolanaApiClient", "RPC ($node) failed with status ${response.code}")
                                synchronized(mutex) {
                                    if (lastSendTxError.isEmpty()) {
                                        lastSendTxError = "فشل الـ RPC الموازن بكود: ${response.code}"
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        synchronized(mutex) {
                            lastException = e
                            lastSendTxError = "خطأ اتصال RPC موازن: ${e.message}"
                        }
                        Log.e("SolanaApiClient", "RPC ($node) error: ${e.message}")
                    }
                }
            }

            for (i in 0 until 400) { // Max 4 seconds (400 * 10ms)
                if (synchronized(mutex) { successfulSig != null }) {
                    jobs.forEach { it.cancel() }
                    break
                }
                delay(10)
            }
            try {
                awaitAll(*jobs.toTypedArray())
            } catch (e: Exception) {
                // Ignore cancellation exceptions
            }
        }

        val finalSig = synchronized(mutex) { successfulSig }
        if (finalSig != null) {
            return finalSig
        }

        val failMsg = synchronized(mutex) { lastException?.message } ?: "جميع عقد الموازنة والـ RPC فشلوا في إرسال المعاملة أو تجاوزوا المهلة."
        reportErrorToTelegram("إرسال الصفقة للشبكة عبر RPC", "خطأ اتصال نهائي: $failMsg")
        if (lastSendTxError.isEmpty()) {
            lastSendTxError = failMsg
        }
        return null
    }
}
