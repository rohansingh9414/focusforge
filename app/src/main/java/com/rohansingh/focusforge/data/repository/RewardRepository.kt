package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.data.dao.RedemptionLogDao
import com.rohansingh.focusforge.data.dao.RewardTemplateDao
import com.rohansingh.focusforge.data.entities.RedemptionLog
import com.rohansingh.focusforge.data.entities.RewardTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing abstraction over RewardTemplate and RedemptionLog data operations.
 */
class RewardRepository(
    private val rewardTemplateDao: RewardTemplateDao,
    private val redemptionLogDao: RedemptionLogDao
) {

    val allRewardTemplates: Flow<List<RewardTemplate>> = rewardTemplateDao.getAllRewardTemplates()

    suspend fun getRewardTemplateById(id: Long): RewardTemplate? {
        return rewardTemplateDao.getRewardTemplateById(id)
    }

    suspend fun insertRewardTemplate(rewardTemplate: RewardTemplate): Long {
        return rewardTemplateDao.insertRewardTemplate(rewardTemplate)
    }

    suspend fun updateRewardTemplate(rewardTemplate: RewardTemplate): Int {
        return rewardTemplateDao.updateRewardTemplate(rewardTemplate)
    }

    suspend fun deleteRewardTemplate(rewardTemplate: RewardTemplate): Int {
        return rewardTemplateDao.deleteRewardTemplate(rewardTemplate)
    }

    suspend fun insertRedemptionLog(redemptionLog: RedemptionLog): Long {
        return redemptionLogDao.insertRedemptionLog(redemptionLog)
    }

    fun getLogsForReward(rewardTemplateId: Long): Flow<List<RedemptionLog>> {
        return redemptionLogDao.getLogsForReward(rewardTemplateId)
    }

    fun getAllLogs(): Flow<List<RedemptionLog>> {
        return redemptionLogDao.getAllLogs()
    }
}
