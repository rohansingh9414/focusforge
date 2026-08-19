package com.rohansingh.focusforge.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rohansingh.focusforge.data.dao.FocusSessionDao
import com.rohansingh.focusforge.data.dao.GoalLogDao
import com.rohansingh.focusforge.data.dao.GoalStreakDao
import com.rohansingh.focusforge.data.dao.GoalTemplateDao
import com.rohansingh.focusforge.data.dao.RedemptionLogDao
import com.rohansingh.focusforge.data.dao.RestrictedAppDao
import com.rohansingh.focusforge.data.dao.RewardTemplateDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.dao.XpLogDao
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalStreak
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.RedemptionLog
import com.rohansingh.focusforge.data.entities.RestrictedApp
import com.rohansingh.focusforge.data.entities.RewardTemplate
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.entities.XpLog

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
        FocusSessionEntity::class,
        XpLog::class,
        GoalStreak::class
    ],
    version = 5,
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
    abstract fun xpLogDao(): XpLogDao
    abstract fun goalStreakDao(): GoalStreakDao

    companion object {
        const val DATABASE_NAME = "focusforge.db"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wallet ADD COLUMN totalXp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS xp_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalTemplateId INTEGER NOT NULL,
                        goalLogId INTEGER NOT NULL,
                        xpEarned INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL,
                        FOREIGN KEY(goalTemplateId) REFERENCES goal_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(goalLogId) REFERENCES goal_logs(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_xp_logs_goalTemplateId ON xp_logs(goalTemplateId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_xp_logs_goalLogId ON xp_logs(goalLogId)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS goal_streaks (
                        goalTemplateId INTEGER PRIMARY KEY NOT NULL,
                        currentStreak INTEGER NOT NULL,
                        longestStreak INTEGER NOT NULL,
                        lastCompletedDate TEXT,
                        FOREIGN KEY(goalTemplateId) REFERENCES goal_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_goal_streaks_goalTemplateId ON goal_streaks(goalTemplateId)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
