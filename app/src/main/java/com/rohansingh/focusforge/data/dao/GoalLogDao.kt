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
}
