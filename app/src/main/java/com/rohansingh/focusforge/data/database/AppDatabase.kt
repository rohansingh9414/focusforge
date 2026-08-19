package com.rohansingh.focusforge.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.entities.Wallet

/**
 * Main Room database for FocusForge.
 */
@Database(entities = [Wallet::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun walletDao(): WalletDao

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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
