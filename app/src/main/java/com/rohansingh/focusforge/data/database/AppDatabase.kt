package com.rohansingh.focusforge.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rohansingh.focusforge.data.dao.FocusSessionDao
import com.rohansingh.focusforge.data.dao.GoalLogDao
import com.rohansingh.focusforge.data.dao.GoalTemplateDao
import com.rohansingh.focusforge.data.dao.RedemptionLogDao
import com.rohansingh.focusforge.data.dao.RestrictedAppDao
import com.rohansingh.focusforge.data.dao.RewardTemplateDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.RedemptionLog
import com.rohansingh.focusforge.data.entities.RestrictedApp
import com.rohansingh.focusforge.data.entities.RewardTemplate
import com.rohansingh.focusforge.data.entities.Wallet

/**
 * Main Room database for FocusForge.
 */
@Database(
    entities = [
        Wallet::class,
        GoalTemplate::class,
        GoalLog::class,
        RewardTemplate::class,
        RedemptionLog::class,
        RestrictedApp::class,
        FocusSessionEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun walletDao(): WalletDao
    abstract fun goalTemplateDao(): GoalTemplateDao
    abstract fun goalLogDao(): GoalLogDao
    abstract fun rewardTemplateDao(): RewardTemplateDao
    abstract fun redemptionLogDao(): RedemptionLogDao
    abstract fun restrictedAppDao(): RestrictedAppDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        const val DATABASE_NAME = "focusforge.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
