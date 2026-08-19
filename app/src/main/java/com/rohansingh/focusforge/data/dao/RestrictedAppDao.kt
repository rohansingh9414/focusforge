package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rohansingh.focusforge.data.entities.RestrictedApp
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for RestrictedApp entities.
 */
@Dao
interface RestrictedAppDao {

    @Query("SELECT * FROM restricted_apps ORDER BY appName ASC")
    fun getAllRestrictedApps(): Flow<List<RestrictedApp>>

    @Query("SELECT * FROM restricted_apps WHERE isRestricted = 1 ORDER BY appName ASC")
    fun getActiveRestrictedApps(): Flow<List<RestrictedApp>>

    @Query("SELECT * FROM restricted_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getRestrictedApp(packageName: String): RestrictedApp?

    @Query("SELECT * FROM restricted_apps WHERE packageName = :packageName LIMIT 1")
    fun observeRestrictedApp(packageName: String): Flow<RestrictedApp?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: RestrictedApp): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<RestrictedApp>): List<Long>

    @Update
    suspend fun update(app: RestrictedApp): Int

    @Query("UPDATE restricted_apps SET isRestricted = :isRestricted WHERE packageName = :packageName")
    suspend fun setRestrictedState(packageName: String, isRestricted: Boolean): Int

    @Delete
    suspend fun delete(app: RestrictedApp): Int

    @Query("DELETE FROM restricted_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String): Int
}
