package com.rohansingh.focusforge.domain.models

import com.rohansingh.focusforge.data.dao.AppScreenTimeUsageStat
import com.rohansingh.focusforge.data.dao.GoalFocusStat
import com.rohansingh.focusforge.data.dao.GoalPerformanceStat
import com.rohansingh.focusforge.data.dao.RewardRedemptionStat

enum class TimePeriod(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days"),
    ALL_TIME("All Time")
}

data class TimeRange(
    val startTimeMs: Long,
    val endTimeMs: Long
)

data class ChartDataPoint(
    val dateLabel: String,
    val dateString: String,
    val value: Double
)

data class DualChartDataPoint(
    val dateLabel: String,
    val dateString: String,
    val earnedValue: Double,
    val spentValue: Double
)

data class EconomyStats(
    val totalCreditsEarned: Double = 0.0,
    val totalCreditsSpent: Double = 0.0,
    val netCredits: Double = 0.0,
    val dailyCreditsTrend: List<DualChartDataPoint> = emptyList()
)

data class GoalsOverviewStats(
    val totalCompletions: Int = 0,
    val goalPerformance: List<GoalPerformanceStat> = emptyList(),
    val dailyCompletionsTrend: List<ChartDataPoint> = emptyList()
)

data class GamificationOverviewStats(
    val periodXpEarned: Long = 0L,
    val totalLifetimeXp: Long = 0L,
    val levelInfo: LevelInfo = LevelInfo(1, 0, 100, 0, 100, 0f),
    val topStreak: Int = 0,
    val longestStreak: Int = 0
)

data class FocusOverviewStats(
    val completedSessionsCount: Int = 0,
    val totalFocusMinutes: Int = 0,
    val avgDurationMinutes: Double = 0.0,
    val goalBreakdown: List<GoalFocusStat> = emptyList()
)

data class RewardsOverviewStats(
    val totalRedemptionsCount: Int = 0,
    val totalCreditsSpent: Double = 0.0,
    val rewardBreakdown: List<RewardRedemptionStat> = emptyList()
)

data class ScreenTimeOverviewStats(
    val currentBalanceMinutes: Int = 0,
    val periodMinutesConsumed: Int = 0,
    val minutesEarnedFromRewards: Double = 0.0,
    val dailyUsageTrend: List<ChartDataPoint> = emptyList(),
    val appUsageBreakdown: List<AppScreenTimeUsageStat> = emptyList()
)

data class StatisticsUiState(
    val selectedPeriod: TimePeriod = TimePeriod.LAST_7_DAYS,
    val isLoading: Boolean = false,
    val economyStats: EconomyStats = EconomyStats(),
    val goalsStats: GoalsOverviewStats = GoalsOverviewStats(),
    val gamificationStats: GamificationOverviewStats = GamificationOverviewStats(),
    val focusStats: FocusOverviewStats = FocusOverviewStats(),
    val rewardsStats: RewardsOverviewStats = RewardsOverviewStats(),
    val screenTimeStats: ScreenTimeOverviewStats = ScreenTimeOverviewStats()
)
