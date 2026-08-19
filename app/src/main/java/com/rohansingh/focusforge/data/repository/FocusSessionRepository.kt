package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.data.dao.FocusSessionDao
import com.rohansingh.focusforge.data.entities.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing FocusSession data access and reactive streams.
 */
open class FocusSessionRepository(
    private val focusSessionDao: FocusSessionDao
) {
    val activeSession: Flow<FocusSessionEntity?> =
        focusSessionDao.getActiveSession()

    open suspend fun getActiveSessionOnce(): FocusSessionEntity? =
        focusSessionDao.getActiveSessionOnce()

    open suspend fun getSessionById(id: Long): FocusSessionEntity? =
        focusSessionDao.getSessionById(id)

    open suspend fun getActiveSessionForGoal(goalId: Long): FocusSessionEntity? =
        focusSessionDao.getActiveSessionForGoal(goalId)

    open suspend fun insertSession(session: FocusSessionEntity): Long =
        focusSessionDao.insertSession(session)

    open suspend fun updateSession(session: FocusSessionEntity): Int =
        focusSessionDao.updateSession(session)

    open suspend fun markSessionCompleted(id: Long, completedAt: Long): Int =
        focusSessionDao.markSessionCompleted(id, completedAt)
}
