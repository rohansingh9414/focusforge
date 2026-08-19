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
}
