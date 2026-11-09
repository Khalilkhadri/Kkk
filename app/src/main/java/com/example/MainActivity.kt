package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.ui.BotDashboardScreen
import com.example.ui.BotViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: BotViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val token = com.example.api.SolanaApiClient.tgBotToken
                val chatId = com.example.api.SolanaApiClient.tgChatId
                val isReal = com.example.api.SolanaApiClient.isRealMode
                
                if (token.isNotEmpty() && chatId.isNotEmpty()) {
                    val stackTrace = throwable.stackTraceToString().take(1200)
                    val info = """
                        🚨 <b>تنبيه انهيار مفاجئ للتطبيق (Crash Report)</b>
                        
                        <b>نوع المعضلة:</b> <code>${throwable.javaClass.simpleName}</code>
                        <b>الرسالة:</b> <code>${throwable.message ?: "من دون تفاصيل"}</code>
                        <b>النمط النشط:</b> ${if (isReal) "تداول حقيقي في الشبكة" else "وضع المحاكاة"}
                        <b>مسار مسببات الانهيار:</b>
                        <pre>$stackTrace</pre>
                        
                        💡 <i>يرجى مراجعة اتصال الشبكة وجودة خادم الـ VPN النشط لضمان سير عمليات الخلفية بسلاسة وعدم توقف البوت.</i>
                    """.trimIndent()
                    
                    val senderThread = Thread {
                        try {
                            com.example.api.SolanaApiClient.sendTelegramNotification(token, chatId, info)
                        } catch (t: Throwable) {
                            // Prevent secondary crash in uncaught handler
                        }
                    }
                    senderThread.start()
                    senderThread.join(1200)
                }
            } catch (e: Throwable) {
                // Prevent recursive crash loops
            } finally {
                originalHandler?.uncaughtException(thread, throwable)
            }
        }

        super.onCreate(savedInstanceState)
        
        // Initialize the Smart Wallet Blacklist Manager
        com.example.api.WalletBlacklistManager.init(applicationContext)
        
        // Request notification permission for Android 13+ to support stable Foreground Service notifications
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        // Request battery optimizations exemption for stable background execution and keeping network active
        val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pkgName = packageName
            if (!pm.isIgnoringBatteryOptimizations(pkgName)) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$pkgName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        android.util.Log.e("MainActivity", "Failed to open battery optimization settings", ex)
                    }
                }
            }
        }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BotDashboardScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
