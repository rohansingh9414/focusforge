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

    @Query("""
        SELECT COALESCE(SUM(xpEarned), 0) 
        FROM xp_logs 
        WHERE completedAt >= :startTimeMs AND completedAt <= :endTimeMs
    """)
    fun getTotalXpEarned(startTimeMs: Long, endTimeMs: Long): Flow<Long>

    @Query("""
        SELECT 
            DATE(completedAt / 1000, 'unixepoch', 'localtime') AS dateString,
            COALESCE(SUM(xpEarned), 0) AS totalXp
        FROM xp_logs
        WHERE completedAt >= :startTimeMs AND completedAt <= :endTimeMs
        GROUP BY dateString
        ORDER BY dateString ASC
    """)
    fun getDailyXpEarned(startTimeMs: Long, endTimeMs: Long): Flow<List<DailyXpStat>>
}

data class DailyXpStat(
    val dateString: String,
    val totalXp: Long
)

