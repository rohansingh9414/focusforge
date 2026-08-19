package com.rohansingh.focusforge.data.repository

import androidx.room.withTransaction
import com.rohansingh.focusforge.data.dao.GoalLogDao
import com.rohansingh.focusforge.data.dao.GoalStreakDao
import com.rohansingh.focusforge.data.dao.GoalTemplateDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.dao.XpLogDao
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalStreak
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.entities.Wallet
import com.rohansingh.focusforge.data.entities.XpLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Repository providing abstraction over GoalTemplate, GoalLog, GoalStreak, and XpLog data operations.
 */
class GoalRepository(
    private val database: AppDatabase? = null,
    private val goalTemplateDao: GoalTemplateDao,
    private val goalLogDao: GoalLogDao,
    private val goalStreakDao: GoalStreakDao? = null,
    private val xpLogDao: XpLogDao? = null,
    private val walletDao: WalletDao? = null
) {

    val allGoalTemplates: Flow<List<GoalTemplate>> = goalTemplateDao.getAllGoalTemplates()
    val allGoalStreaks: Flow<List<GoalStreak>> = goalStreakDao?.getAllStreaks() ?: emptyFlow()

    suspend fun getGoalTemplateById(id: Long): GoalTemplate? {
        return goalTemplateDao.getGoalTemplateById(id)
    }

    suspend fun insertGoalTemplate(goalTemplate: GoalTemplate): Long {
        return goalTemplateDao.insertGoalTemplate(goalTemplate)
    }

    suspend fun updateGoalTemplate(goalTemplate: GoalTemplate): Int {
        return goalTemplateDao.updateGoalTemplate(goalTemplate)
    }

    suspend fun deleteGoalTemplate(goalTemplate: GoalTemplate): Int {
        return goalTemplateDao.deleteGoalTemplate(goalTemplate)
    }

    suspend fun insertGoalLog(goalLog: GoalLog): Long {
        return goalLogDao.insertGoalLog(goalLog)
    }

    fun getLogsForGoal(goalTemplateId: Long): Flow<List<GoalLog>> {
        return goalLogDao.getLogsForGoal(goalTemplateId)
    }

    suspend fun getCreditsEarnedToday(
        goalTemplateId: Long,
        startOfDayTimestamp: Long,
        endOfDayTimestamp: Long
    ): Double {
        return goalLogDao.getCreditsEarnedToday(
            goalTemplateId = goalTemplateId,
            startOfDayTimestamp = startOfDayTimestamp,
            endOfDayTimestamp = endOfDayTimestamp
        )
    }

    suspend fun getStreakForGoal(goalTemplateId: Long): GoalStreak? {
        return goalStreakDao?.getStreakForGoalOnce(goalTemplateId)
    }

    fun observeStreakForGoal(goalTemplateId: Long): Flow<GoalStreak?> {
        return goalStreakDao?.getStreakForGoal(goalTemplateId) ?: emptyFlow()
    }

    /**
     * Atomically executes a goal completion in a single database transaction:
     * 1. Inserts GoalLog
     * 2. Inserts XpLog (if xpEarned > 0)
     * 3. Upserts GoalStreak
     * 4. Updates Wallet (creditBalance and totalXp)
     */
    suspend fun recordGoalCompletionTransaction(
        goalLog: GoalLog,
        xpEarned: Long,
        updatedStreak: GoalStreak,
        creditsEarned: Double
    ) {
        val block: suspend () -> Unit = {
            val goalLogId = goalLogDao.insertGoalLog(goalLog)
            if (xpEarned > 0L && xpLogDao != null) {
                val xpLog = XpLog(
                    goalTemplateId = goalLog.goalTemplateId,
                    goalLogId = goalLogId,
                    xpEarned = xpEarned,
                    completedAt = goalLog.completedAt
                )
                xpLogDao.insertXpLog(xpLog)
            }
            goalStreakDao?.insertOrUpdate(updatedStreak)

            if ((creditsEarned > 0.0 || xpEarned > 0L) && walletDao != null) {
                val currentWallet = walletDao.getWalletOnce() ?: Wallet()
                val updatedWallet = currentWallet.copy(
                    creditBalance = currentWallet.creditBalance + creditsEarned,
                    totalXp = currentWallet.totalXp + xpEarned
                )
                walletDao.updateWallet(updatedWallet)
            }
        }

        if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }
}
