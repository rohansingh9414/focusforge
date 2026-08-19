package com.rohansingh.focusforge.data.database

import androidx.room.RoomDatabase

/**
 * Room database scaffold for FocusForge.
 *
 * Phase 1 Foundation:
 * This class establishes the database architectural scaffold in data/database/.
 * Because Room 2.6.1 requires at least one @Entity to generate a concrete
 * implementation (AppDatabase_Impl), this class serves as a pre-entity abstract
 * RoomDatabase foundation without claiming a runtime database instance.
 *
 * In Phase 2:
 * Upon introducing the first entity (Wallet), this class will be annotated with:
 * `@Database(entities = [Wallet::class], version = 1, exportSchema = false)`
 * and will provide DAO accessors and the singleton database builder.
 */
abstract class AppDatabase : RoomDatabase()
