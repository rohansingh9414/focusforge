package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rohansingh.focusforge.data.entities.GoalStreak
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for Goal streaks.
 */
@Dao
interface GoalStreakDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(streak: GoalStreak): Long

    @Query("SELECT * FROM goal_streaks WHERE goalTemplateId = :goalId")
    suspend fun getStreakForGoalOnce(goalId: Long): GoalStreak?

    @Query("SELECT * FROM goal_streaks WHERE goalTemplateId = :goalId")
    fun getStreakForGoal(goalId: Long): Flow<GoalStreak?>

    @Query("SELECT * FROM goal_streaks")
    fun getAllStreaks(): Flow<List<GoalStreak>>

    @Query("DELETE FROM goal_streaks WHERE goalTemplateId = :goalId")
    suspend fun deleteStreakForGoal(goalId: Long): Int
}
