package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.data.dao.FocusSessionDao
import com.rohansingh.focusforge.data.dao.GoalLogDao
import com.rohansingh.focusforge.data.dao.GoalStreakDao
import com.rohansingh.focusforge.data.dao.RedemptionLogDao
import com.rohansingh.focusforge.data.dao.ScreenTimeLogDao
import com.rohansingh.focusforge.data.dao.WalletDao
import com.rohansingh.focusforge.data.dao.XpLogDao
import com.rohansingh.focusforge.domain.gamification.LevelCalculator
import com.rohansingh.focusforge.domain.models.ChartDataPoint
import com.rohansingh.focusforge.domain.models.DualChartDataPoint
import com.rohansingh.focusforge.domain.models.EconomyStats
import com.rohansingh.focusforge.domain.models.FocusOverviewStats
import com.rohansingh.focusforge.domain.models.GamificationOverviewStats
import com.rohansingh.focusforge.domain.models.GoalsOverviewStats
import com.rohansingh.focusforge.domain.models.RewardsOverviewStats
import com.rohansingh.focusforge.domain.models.ScreenTimeOverviewStats
import com.rohansingh.focusforge.domain.models.TimePeriod
import com.rohansingh.focusforge.domain.models.TimeRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Repository providing read-only aggregated statistics and trends across all domain entities.
 */
class StatisticsRepository(
    private val goalLogDao: GoalLogDao,
    private val redemptionLogDao: RedemptionLogDao,
    private val xpLogDao: XpLogDao,
    private val goalStreakDao: GoalStreakDao,
    private val focusSessionDao: FocusSessionDao,
    private val screenTimeLogDao: ScreenTimeLogDao,
    private val walletDao: WalletDao
) {

    companion object {
        fun calculateTimeRange(period: TimePeriod, calendar: Calendar = Calendar.getInstance()): TimeRange {
            val endCal = calendar.clone() as Calendar
            endCal.set(Calendar.HOUR_OF_DAY, 23)
            endCal.set(Calendar.MINUTE, 59)
            endCal.set(Calendar.SECOND, 59)
            endCal.set(Calendar.MILLISECOND, 999)
            val endTimeMs = endCal.timeInMillis

            return when (period) {
                TimePeriod.TODAY -> {
                    val startCal = calendar.clone() as Calendar
                    startCal.set(Calendar.HOUR_OF_DAY, 0)
                    startCal.set(Calendar.MINUTE, 0)
                    startCal.set(Calendar.SECOND, 0)
                    startCal.set(Calendar.MILLISECOND, 0)
                    TimeRange(startCal.timeInMillis, endTimeMs)
                }
                TimePeriod.LAST_7_DAYS -> {
                    val startCal = calendar.clone() as Calendar
                    startCal.add(Calendar.DAY_OF_YEAR, -6)
                    startCal.set(Calendar.HOUR_OF_DAY, 0)
                    startCal.set(Calendar.MINUTE, 0)
                    startCal.set(Calendar.SECOND, 0)
                    startCal.set(Calendar.MILLISECOND, 0)
                    TimeRange(startCal.timeInMillis, endTimeMs)
                }
                TimePeriod.LAST_30_DAYS -> {
                    val startCal = calendar.clone() as Calendar
                    startCal.add(Calendar.DAY_OF_YEAR, -29)
                    startCal.set(Calendar.HOUR_OF_DAY, 0)
                    startCal.set(Calendar.MINUTE, 0)
                    startCal.set(Calendar.SECOND, 0)
                    startCal.set(Calendar.MILLISECOND, 0)
                    TimeRange(startCal.timeInMillis, endTimeMs)
                }
                TimePeriod.ALL_TIME -> {
                    TimeRange(0L, Long.MAX_VALUE)
                }
            }
        }

        fun generateZeroFilledDateSeries(
            period: TimePeriod,
            calendar: Calendar = Calendar.getInstance()
        ): List<Pair<String, String>> {
            val dateForm = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return when (period) {
                TimePeriod.TODAY -> {
                    val labelForm = SimpleDateFormat("HH:mm", Locale.getDefault())
                    val d = calendar.time
                    listOf(dateForm.format(d) to "Today")
                }
                TimePeriod.LAST_7_DAYS -> {
                    val labelForm = SimpleDateFormat("EEE", Locale.getDefault())
                    val list = mutableListOf<Pair<String, String>>()
                    val cal = calendar.clone() as Calendar
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    for (i in 0 until 7) {
                        list.add(dateForm.format(cal.time) to labelForm.format(cal.time))
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    list
                }
                TimePeriod.LAST_30_DAYS -> {
                    val labelForm = SimpleDateFormat("d MMM", Locale.getDefault())
                    val list = mutableListOf<Pair<String, String>>()
                    val cal = calendar.clone() as Calendar
                    cal.add(Calendar.DAY_OF_YEAR, -29)
                    for (i in 0 until 30) {
                        list.add(dateForm.format(cal.time) to labelForm.format(cal.time))
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    list
                }
                TimePeriod.ALL_TIME -> emptyList()
            }
        }
    }

    fun getEconomyStats(period: TimePeriod, range: TimeRange): Flow<EconomyStats> {
        val earnedFlow = goalLogDao.getTotalCreditsEarned(range.startTimeMs, range.endTimeMs)
        val spentFlow = redemptionLogDao.getTotalCreditsSpent(range.startTimeMs, range.endTimeMs)
        val dailyEarnedFlow = goalLogDao.getDailyCreditsEarned(range.startTimeMs, range.endTimeMs)
        val dailySpentFlow = redemptionLogDao.getDailyCreditsSpent(range.startTimeMs, range.endTimeMs)

        return combine(earnedFlow, spentFlow, dailyEarnedFlow, dailySpentFlow) { earned, spent, dailyEarned, dailySpent ->
            val earnedMap = dailyEarned.associate { it.dateString to it.totalCredits }
            val spentMap = dailySpent.associate { it.dateString to it.totalCreditsSpent }

            val templateSeries = generateZeroFilledDateSeries(period)
            val trendPoints = if (templateSeries.isNotEmpty()) {
                templateSeries.map { (dateStr, label) ->
                    DualChartDataPoint(
                        dateLabel = label,
                        dateString = dateStr,
                        earnedValue = earnedMap[dateStr] ?: 0.0,
                        spentValue = spentMap[dateStr] ?: 0.0
                    )
                }
            } else {
                val allDates = (earnedMap.keys + spentMap.keys).sorted()
                val labelForm = SimpleDateFormat("d MMM", Locale.getDefault())
                val parseForm = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                allDates.map { dateStr ->
                    val label = try {
                        val parsed = parseForm.parse(dateStr)
                        if (parsed != null) labelForm.format(parsed) else dateStr
                    } catch (e: Exception) {
                        dateStr
                    }
                    DualChartDataPoint(
                        dateLabel = label,
                        dateString = dateStr,
                        earnedValue = earnedMap[dateStr] ?: 0.0,
                        spentValue = spentMap[dateStr] ?: 0.0
                    )
                }
            }

            EconomyStats(
                totalCreditsEarned = earned,
                totalCreditsSpent = spent,
                netCredits = earned - spent,
                dailyCreditsTrend = trendPoints
            )
        }
    }

    fun getGoalsStats(period: TimePeriod, range: TimeRange): Flow<GoalsOverviewStats> {
        val countFlow = goalLogDao.getTotalCompletionsCount(range.startTimeMs, range.endTimeMs)
        val perfFlow = goalLogDao.getGoalPerformanceStats(range.startTimeMs, range.endTimeMs)
        val dailyTrendFlow = goalLogDao.getDailyCreditsEarned(range.startTimeMs, range.endTimeMs)

        return combine(countFlow, perfFlow, dailyTrendFlow) { count, perf, dailyList ->
            val countsMap = dailyList.associate { it.dateString to it.completionCount.toDouble() }
            val templateSeries = generateZeroFilledDateSeries(period)
            val trendPoints = if (templateSeries.isNotEmpty()) {
                templateSeries.map { (dateStr, label) ->
                    ChartDataPoint(
                        dateLabel = label,
                        dateString = dateStr,
                        value = countsMap[dateStr] ?: 0.0
                    )
                }
            } else {
                val labelForm = SimpleDateFormat("d MMM", Locale.getDefault())
                val parseForm = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                countsMap.keys.sorted().map { dateStr ->
                    val label = try {
                        val parsed = parseForm.parse(dateStr)
                        if (parsed != null) labelForm.format(parsed) else dateStr
                    } catch (e: Exception) {
                        dateStr
                    }
                    ChartDataPoint(
                        dateLabel = label,
                        dateString = dateStr,
                        value = countsMap[dateStr] ?: 0.0
                    )
                }
            }

            GoalsOverviewStats(
                totalCompletions = count,
                goalPerformance = perf,
                dailyCompletionsTrend = trendPoints
            )
        }
    }

    fun getGamificationStats(range: TimeRange): Flow<GamificationOverviewStats> {
        val periodXpFlow = xpLogDao.getTotalXpEarned(range.startTimeMs, range.endTimeMs)
        val walletFlow = walletDao.getWallet()
        val streaksFlow = goalStreakDao.getAllStreaks()

        return combine(periodXpFlow, walletFlow, streaksFlow) { periodXp, wallet, streaks ->
            val lifetimeXp = wallet?.totalXp ?: 0L
            val levelInfo = LevelCalculator.calculateLevel(lifetimeXp)
            val topStreak = streaks.maxOfOrNull { it.currentStreak } ?: 0
            val longestStreak = streaks.maxOfOrNull { it.longestStreak } ?: 0

            GamificationOverviewStats(
                periodXpEarned = periodXp,
                totalLifetimeXp = lifetimeXp,
                levelInfo = levelInfo,
                topStreak = topStreak,
                longestStreak = longestStreak
            )
        }
    }

    fun getFocusStats(range: TimeRange): Flow<FocusOverviewStats> {
        val summaryFlow = focusSessionDao.getFocusSessionSummary(range.startTimeMs, range.endTimeMs)
        val breakdownFlow = focusSessionDao.getGoalFocusBreakdown(range.startTimeMs, range.endTimeMs)

        return combine(summaryFlow, breakdownFlow) { summary, breakdown ->
            FocusOverviewStats(
                completedSessionsCount = summary.completedSessionsCount,
                totalFocusMinutes = summary.totalFocusMinutes,
                avgDurationMinutes = summary.avgDurationMinutes,
                goalBreakdown = breakdown
            )
        }
    }

    fun getRewardsStats(range: TimeRange): Flow<RewardsOverviewStats> {
        val countFlow = redemptionLogDao.getTotalRedemptionsCount(range.startTimeMs, range.endTimeMs)
        val spentFlow = redemptionLogDao.getTotalCreditsSpent(range.startTimeMs, range.endTimeMs)
        val breakdownFlow = redemptionLogDao.getRewardRedemptionStats(range.startTimeMs, range.endTimeMs)

        return combine(countFlow, spentFlow, breakdownFlow) { count, spent, breakdown ->
            RewardsOverviewStats(
                totalRedemptionsCount = count,
                totalCreditsSpent = spent,
                rewardBreakdown = breakdown
            )
        }
    }

    fun getScreenTimeStats(period: TimePeriod, range: TimeRange): Flow<ScreenTimeOverviewStats> {
        val consumedFlow = screenTimeLogDao.getTotalScreenTimeConsumed(range.startTimeMs, range.endTimeMs)
        val redeemedMinFlow = redemptionLogDao.getScreenTimeMinutesRedeemed(range.startTimeMs, range.endTimeMs)
        val walletFlow = walletDao.getWallet()
        val dailyUsageFlow = screenTimeLogDao.getDailyScreenTimeUsage(range.startTimeMs, range.endTimeMs)
        val appUsageFlow = screenTimeLogDao.getAppScreenTimeUsage(range.startTimeMs, range.endTimeMs)

        return combine(consumedFlow, redeemedMinFlow, walletFlow, dailyUsageFlow, appUsageFlow) { consumed, redeemed, wallet, dailyList, appList ->
            val dailyMap = dailyList.associate { it.dateString to it.totalMinutes.toDouble() }
            val templateSeries = generateZeroFilledDateSeries(period)
            val trendPoints = if (templateSeries.isNotEmpty()) {
                templateSeries.map { (dateStr, label) ->
                    ChartDataPoint(
                        dateLabel = label,
                        dateString = dateStr,
                        value = dailyMap[dateStr] ?: 0.0
                    )
                }
            } else {
                val labelForm = SimpleDateFormat("d MMM", Locale.getDefault())
                val parseForm = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                dailyMap.keys.sorted().map { dateStr ->
                    val label = try {
                        val parsed = parseForm.parse(dateStr)
                        if (parsed != null) labelForm.format(parsed) else dateStr
                    } catch (e: Exception) {
                        dateStr
                    }
                    ChartDataPoint(
                        dateLabel = label,
                        dateString = dateStr,
                        value = dailyMap[dateStr] ?: 0.0
                    )
                }
            }

            ScreenTimeOverviewStats(
                currentBalanceMinutes = wallet?.screenTimeMinutes ?: 0,
                periodMinutesConsumed = consumed,
                minutesEarnedFromRewards = redeemed,
                dailyUsageTrend = trendPoints,
                appUsageBreakdown = appList
            )
        }
    }
}
