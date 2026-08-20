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

    @Query("""
        SELECT COALESCE(SUM(creditsSpent), 0.0) 
        FROM redemption_logs 
        WHERE redeemedAt >= :startTimeMs AND redeemedAt <= :endTimeMs
    """)
    fun getTotalCreditsSpent(startTimeMs: Long, endTimeMs: Long): Flow<Double>

    @Query("""
        SELECT COUNT(*) 
        FROM redemption_logs 
        WHERE redeemedAt >= :startTimeMs AND redeemedAt <= :endTimeMs
    """)
    fun getTotalRedemptionsCount(startTimeMs: Long, endTimeMs: Long): Flow<Int>

    @Query("""
        SELECT 
            DATE(redeemedAt / 1000, 'unixepoch', 'localtime') AS dateString,
            COALESCE(SUM(creditsSpent), 0.0) AS totalCreditsSpent,
            COUNT(*) AS redemptionCount
        FROM redemption_logs
        WHERE redeemedAt >= :startTimeMs AND redeemedAt <= :endTimeMs
        GROUP BY dateString
        ORDER BY dateString ASC
    """)
    fun getDailyCreditsSpent(startTimeMs: Long, endTimeMs: Long): Flow<List<DailyCreditsSpentStat>>

    @Query("""
        SELECT 
            rl.rewardTemplateId AS rewardTemplateId,
            rt.title AS rewardTitle,
            rt.unit AS rewardUnit,
            COALESCE(SUM(rl.unitsRedeemed), 0.0) AS totalUnits,
            COALESCE(SUM(rl.creditsSpent), 0.0) AS totalCreditsSpent,
            COUNT(rl.id) AS redemptionCount
        FROM redemption_logs rl
        INNER JOIN reward_templates rt ON rl.rewardTemplateId = rt.id
        WHERE rl.redeemedAt >= :startTimeMs AND rl.redeemedAt <= :endTimeMs
        GROUP BY rl.rewardTemplateId
        ORDER BY totalCreditsSpent DESC
    """)
    fun getRewardRedemptionStats(startTimeMs: Long, endTimeMs: Long): Flow<List<RewardRedemptionStat>>

    @Query("""
        SELECT COALESCE(SUM(rl.unitsRedeemed), 0.0)
        FROM redemption_logs rl
        INNER JOIN reward_templates rt ON rl.rewardTemplateId = rt.id
        WHERE rt.rewardType = 'SCREEN_TIME'
          AND rl.redeemedAt >= :startTimeMs 
          AND rl.redeemedAt <= :endTimeMs
    """)
    fun getScreenTimeMinutesRedeemed(startTimeMs: Long, endTimeMs: Long): Flow<Double>
}

data class DailyCreditsSpentStat(
    val dateString: String,
    val totalCreditsSpent: Double,
    val redemptionCount: Int
)

data class RewardRedemptionStat(
    val rewardTemplateId: Long,
    val rewardTitle: String,
    val rewardUnit: String,
    val totalUnits: Double,
    val totalCreditsSpent: Double,
    val redemptionCount: Int
)

