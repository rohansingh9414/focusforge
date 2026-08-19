package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rohansingh.focusforge.data.entities.XpLog
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for XP transaction logs.
 */
@Dao
interface XpLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertXpLog(xpLog: XpLog): Long

    @Query("SELECT * FROM xp_logs ORDER BY completedAt DESC")
    fun getAllXpLogs(): Flow<List<XpLog>>

    @Query("SELECT SUM(xpEarned) FROM xp_logs")
    suspend fun getTotalXp(): Long?

    @Query("SELECT * FROM xp_logs WHERE goalTemplateId = :goalId ORDER BY completedAt DESC")
    fun getXpLogsForGoal(goalId: Long): Flow<List<XpLog>>

    @Query("DELETE FROM xp_logs WHERE goalTemplateId = :goalId")
    suspend fun deleteXpLogsForGoal(goalId: Long): Int
}
