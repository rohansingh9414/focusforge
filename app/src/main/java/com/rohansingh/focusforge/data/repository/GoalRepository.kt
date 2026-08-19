package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.data.dao.GoalLogDao
import com.rohansingh.focusforge.data.dao.GoalTemplateDao
import com.rohansingh.focusforge.data.entities.GoalLog
import com.rohansingh.focusforge.data.entities.GoalTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing abstraction over GoalTemplate and GoalLog data operations.
 */
class GoalRepository(
    private val goalTemplateDao: GoalTemplateDao,
    private val goalLogDao: GoalLogDao
) {

    val allGoalTemplates: Flow<List<GoalTemplate>> = goalTemplateDao.getAllGoalTemplates()

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
}
