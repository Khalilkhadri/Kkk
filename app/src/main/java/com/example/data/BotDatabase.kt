package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [BotSettings::class, TradeLog::class, SniperTradeLog::class], version = 18, exportSchema = false)
abstract class BotDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun logDao(): LogDao
    abstract fun sniperLogDao(): SniperLogDao

    companion object {
        @Volatile
        private var INSTANCE: BotDatabase? = null

        fun getDatabase(context: Context): BotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = try {
                    Room.databaseBuilder(
                        context.applicationContext,
                        BotDatabase::class.java,
                        "solana_arb_bot_db_v7"
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()
                } catch (e: Throwable) {
                    Room.inMemoryDatabaseBuilder(
                        context.applicationContext,
                        BotDatabase::class.java
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()
                }
                INSTANCE = instance
                instance
            }
        }
    }
}
