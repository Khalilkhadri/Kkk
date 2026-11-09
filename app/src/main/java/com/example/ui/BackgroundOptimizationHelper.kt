package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BackgroundOptimizationHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true
        }
    }

    fun isDataSaverRestricting(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                val status = connectivityManager.restrictBackgroundStatus
                return status == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            }
        }
        return false
    }

    fun requestBatteryExemption(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pkg = context.packageName
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$pkg")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e("BgOptHelper", "Failed to start battery optimization settings", ex)
                }
            }
        }
    }

    fun requestUnrestrictedData(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val pkg = context.packageName
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (ex: Exception) {
                    Log.e("BgOptHelper", "Failed to start app details settings for background data", ex)
                }
            }
        }
    }

    fun openAutostartSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
        val intents = mutableListOf<Intent>()

        when {
            manufacturer.contains("xiaomi") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                })
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
                })
            }
            manufacturer.contains("samsung") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.samsung.android.sm_cn", "com.samsung.android.sm.ui.battery.BatteryActivity")
                })
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
                })
                intents.add(Intent().apply {
                    component = ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
                })
            }
            manufacturer.contains("vivo") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
                })
            }
            manufacturer.contains("oneplus") -> {
                intents.add(Intent().apply {
                    component = ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.intelligentlaunch.AppLaunchRelationReleaseActivity")
                })
            }
        }

        val pkg = context.packageName
        intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
        })

        for (intent in intents) {
            try {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.d("BgOptHelper", "Failed to launch custom manufacturer intent: ${intent.component?.className}")
            }
        }
        return false
    }
}
