package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for FocusSessionEntity operations.
 */
@Dao
interface FocusSessionDao {

    @Query("SELECT * FROM focus_sessions WHERE status = 'RUNNING' ORDER BY id DESC LIMIT 1")
    fun getActiveSession(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status = 'RUNNING' ORDER BY id DESC LIMIT 1")
    suspend fun getActiveSessionOnce(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions WHERE goalId = :goalId AND status = 'RUNNING' LIMIT 1")
    suspend fun getActiveSessionForGoal(goalId: Long): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions ORDER BY id DESC")
    fun getAllSessions(): Flow<List<FocusSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity): Long

    @Update
    suspend fun updateSession(session: FocusSessionEntity): Int

    @Query("UPDATE focus_sessions SET status = 'COMPLETED', completedAtWallClockMs = :completedAt WHERE id = :id")
    suspend fun markSessionCompleted(id: Long, completedAt: Long): Int

    @Query("""
        SELECT 
            COUNT(*) AS completedSessionsCount,
            COALESCE(SUM(
                CASE 
                    WHEN completedAtWallClockMs IS NOT NULL AND completedAtWallClockMs > startedAtWallClockMs 
                    THEN CAST((completedAtWallClockMs - startedAtWallClockMs) / 60000 AS INTEGER)
                    ELSE targetDurationMinutes 
                END
            ), 0) AS totalFocusMinutes,
            COALESCE(AVG(
                CASE 
                    WHEN completedAtWallClockMs IS NOT NULL AND completedAtWallClockMs > startedAtWallClockMs 
                    THEN CAST((completedAtWallClockMs - startedAtWallClockMs) / 60000 AS REAL)
                    ELSE CAST(targetDurationMinutes AS REAL)
                END
            ), 0.0) AS avgDurationMinutes
        FROM focus_sessions
        WHERE status = 'COMPLETED' 
          AND startedAtWallClockMs >= :startTimeMs 
          AND startedAtWallClockMs <= :endTimeMs
    """)
    fun getFocusSessionSummary(startTimeMs: Long, endTimeMs: Long): Flow<FocusSessionSummaryStat>

    @Query("""
        SELECT 
            goalId,
            snapshotGoalTitle AS goalTitle,
            COUNT(id) AS sessionCount,
            COALESCE(SUM(
                CASE 
                    WHEN completedAtWallClockMs IS NOT NULL AND completedAtWallClockMs > startedAtWallClockMs 
                    THEN CAST((completedAtWallClockMs - startedAtWallClockMs) / 60000 AS INTEGER)
                    ELSE targetDurationMinutes 
                END
            ), 0) AS totalFocusMinutes
        FROM focus_sessions
        WHERE status = 'COMPLETED' 
          AND startedAtWallClockMs >= :startTimeMs 
          AND startedAtWallClockMs <= :endTimeMs
        GROUP BY goalId
        ORDER BY totalFocusMinutes DESC
    """)
    fun getGoalFocusBreakdown(startTimeMs: Long, endTimeMs: Long): Flow<List<GoalFocusStat>>

    @Query("""
        SELECT 
            DATE(startedAtWallClockMs / 1000, 'unixepoch', 'localtime') AS dateString,
            COALESCE(SUM(
                CASE 
                    WHEN completedAtWallClockMs IS NOT NULL AND completedAtWallClockMs > startedAtWallClockMs 
                    THEN CAST((completedAtWallClockMs - startedAtWallClockMs) / 60000 AS INTEGER)
                    ELSE targetDurationMinutes 
                END
            ), 0) AS totalMinutes,
            COUNT(id) AS sessionCount
        FROM focus_sessions
        WHERE status = 'COMPLETED'
          AND startedAtWallClockMs >= :startTimeMs 
          AND startedAtWallClockMs <= :endTimeMs
        GROUP BY dateString
        ORDER BY dateString ASC
    """)
    fun getDailyFocusTrend(startTimeMs: Long, endTimeMs: Long): Flow<List<DailyFocusStat>>
}

data class FocusSessionSummaryStat(
    val completedSessionsCount: Int,
    val totalFocusMinutes: Int,
    val avgDurationMinutes: Double
)

data class GoalFocusStat(
    val goalId: Long,
    val goalTitle: String,
    val sessionCount: Int,
    val totalFocusMinutes: Int
)

data class DailyFocusStat(
    val dateString: String,
    val totalMinutes: Int,
    val sessionCount: Int
)

