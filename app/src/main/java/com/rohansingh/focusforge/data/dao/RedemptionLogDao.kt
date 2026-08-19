package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rohansingh.focusforge.data.entities.RedemptionLog
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for RedemptionLog records.
 */
@Dao
interface RedemptionLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRedemptionLog(redemptionLog: RedemptionLog): Long

    @Query("SELECT * FROM redemption_logs WHERE rewardTemplateId = :rewardTemplateId ORDER BY redeemedAt DESC")
    fun getLogsForReward(rewardTemplateId: Long): Flow<List<RedemptionLog>>

    @Query("SELECT * FROM redemption_logs ORDER BY redeemedAt DESC")
    fun getAllLogs(): Flow<List<RedemptionLog>>
}
