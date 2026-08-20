package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rohansingh.focusforge.data.entities.GoalTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for GoalTemplate entities.
 */
@Dao
interface GoalTemplateDao {

    @Query("SELECT * FROM goal_templates ORDER BY id DESC")
    fun getAllGoalTemplates(): Flow<List<GoalTemplate>>

    @Query("SELECT * FROM goal_templates ORDER BY id DESC")
    suspend fun getAllGoalTemplatesList(): List<GoalTemplate>

    @Query("SELECT * FROM goal_templates WHERE id = :id")
    suspend fun getGoalTemplateById(id: Long): GoalTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoalTemplate(goalTemplate: GoalTemplate): Long

    @Update
    suspend fun updateGoalTemplate(goalTemplate: GoalTemplate): Int

    @Delete
    suspend fun deleteGoalTemplate(goalTemplate: GoalTemplate): Int

    @Query("DELETE FROM goal_templates WHERE id = :id")
    suspend fun deleteGoalTemplateById(id: Long): Int
}
