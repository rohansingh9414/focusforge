package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rohansingh.focusforge.data.entities.GoalLog
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for GoalLog records.
 */
@Dao
interface GoalLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalLog(goalLog: GoalLog): Long

    @Query("SELECT * FROM goal_logs WHERE goalTemplateId = :goalTemplateId ORDER BY completedAt DESC")
    fun getLogsForGoal(goalTemplateId: Long): Flow<List<GoalLog>>

    @Query("SELECT * FROM goal_logs ORDER BY completedAt DESC")
    fun getAllLogs(): Flow<List<GoalLog>>

    @Query("SELECT COALESCE(SUM(creditsEarned), 0.0) FROM goal_logs WHERE goalTemplateId = :goalTemplateId AND completedAt >= :startOfDayTimestamp AND completedAt <= :endOfDayTimestamp")
    suspend fun getCreditsEarnedToday(goalTemplateId: Long, startOfDayTimestamp: Long, endOfDayTimestamp: Long): Double


    @Query("""
        SELECT COALESCE(SUM(creditsEarned), 0.0) 
        FROM goal_logs 
        WHERE completedAt >= :startTimeMs AND completedAt <= :endTimeMs
    """)
    fun getTotalCreditsEarned(startTimeMs: Long, endTimeMs: Long): Flow<Double>

    @Query("""
        SELECT COUNT(*) 
        FROM goal_logs 
        WHERE completedAt >= :startTimeMs AND completedAt <= :endTimeMs
    """)
    fun getTotalCompletionsCount(startTimeMs: Long, endTimeMs: Long): Flow<Int>

    @Query("""
        SELECT 
            DATE(completedAt / 1000, 'unixepoch', 'localtime') AS dateString,
            COALESCE(SUM(creditsEarned), 0.0) AS totalCredits,
            COUNT(*) AS completionCount
        FROM goal_logs
        WHERE completedAt >= :startTimeMs AND completedAt <= :endTimeMs
        GROUP BY dateString
        ORDER BY dateString ASC
    """)
    fun getDailyCreditsEarned(startTimeMs: Long, endTimeMs: Long): Flow<List<DailyCreditsStat>>

    @Query("""
        SELECT 
            gl.goalTemplateId AS goalTemplateId,
            gt.title AS goalTitle,
            gt.unit AS goalUnit,
            COALESCE(SUM(gl.amountCompleted), 0.0) AS totalAmount,
            COALESCE(SUM(gl.creditsEarned), 0.0) AS totalCredits,
            COUNT(gl.id) AS completionCount
        FROM goal_logs gl
        INNER JOIN goal_templates gt ON gl.goalTemplateId = gt.id
        WHERE gl.completedAt >= :startTimeMs AND gl.completedAt <= :endTimeMs
        GROUP BY gl.goalTemplateId
        ORDER BY totalCredits DESC
    """)
    fun getGoalPerformanceStats(startTimeMs: Long, endTimeMs: Long): Flow<List<GoalPerformanceStat>>
}

data class DailyCreditsStat(
    val dateString: String,
    val totalCredits: Double,
    val completionCount: Int
)

data class GoalPerformanceStat(
    val goalTemplateId: Long,
    val goalTitle: String,
    val goalUnit: String,
    val totalAmount: Double,
    val totalCredits: Double,
    val completionCount: Int
)

