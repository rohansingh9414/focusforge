package com.rohansingh.focusforge.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rohansingh.focusforge.data.entities.ScreenTimeLog
import kotlinx.coroutines.flow.Flow

data class DailyScreenTimeStat(
    val dateString: String,
    val totalMinutes: Int
)

data class AppScreenTimeUsageStat(
    val packageName: String,
    val appName: String?,
    val totalMinutes: Int,
    val sessionCount: Int
)

/**
 * Data Access Object for ScreenTimeLog entities.
 */
@Dao
interface ScreenTimeLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ScreenTimeLog): Long

    @Query("""
        SELECT COALESCE(SUM(minutesConsumed), 0) 
        FROM screen_time_logs 
        WHERE consumedAt >= :startTimeMs AND consumedAt <= :endTimeMs
    """)
    fun getTotalScreenTimeConsumed(startTimeMs: Long, endTimeMs: Long): Flow<Int>

    @Query("""
        SELECT 
            DATE(consumedAt / 1000, 'unixepoch', 'localtime') AS dateString,
            COALESCE(SUM(minutesConsumed), 0) AS totalMinutes
        FROM screen_time_logs
        WHERE consumedAt >= :startTimeMs AND consumedAt <= :endTimeMs
        GROUP BY dateString
        ORDER BY dateString ASC
    """)
    fun getDailyScreenTimeUsage(startTimeMs: Long, endTimeMs: Long): Flow<List<DailyScreenTimeStat>>

    @Query("""
        SELECT 
            packageName,
            appName,
            COALESCE(SUM(minutesConsumed), 0) AS totalMinutes,
            COUNT(id) AS sessionCount
        FROM screen_time_logs
        WHERE consumedAt >= :startTimeMs AND consumedAt <= :endTimeMs
        GROUP BY packageName
        ORDER BY totalMinutes DESC
    """)
    fun getAppScreenTimeUsage(startTimeMs: Long, endTimeMs: Long): Flow<List<AppScreenTimeUsageStat>>

    @Query("SELECT * FROM screen_time_logs ORDER BY consumedAt DESC")
    fun getAllLogs(): Flow<List<ScreenTimeLog>>
}
