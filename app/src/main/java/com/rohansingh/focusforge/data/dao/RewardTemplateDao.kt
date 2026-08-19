package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.rohansingh.focusforge.data.entities.RewardTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for RewardTemplate entities.
 */
@Dao
interface RewardTemplateDao {

    @Query("SELECT * FROM reward_templates ORDER BY id DESC")
    fun getAllRewardTemplates(): Flow<List<RewardTemplate>>

    @Query("SELECT * FROM reward_templates WHERE id = :id")
    suspend fun getRewardTemplateById(id: Long): RewardTemplate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewardTemplate(rewardTemplate: RewardTemplate): Long

    @Update
    suspend fun updateRewardTemplate(rewardTemplate: RewardTemplate): Int

    @Delete
    suspend fun deleteRewardTemplate(rewardTemplate: RewardTemplate): Int

    @Query("DELETE FROM reward_templates WHERE id = :id")
    suspend fun deleteRewardTemplateById(id: Long): Int
}
