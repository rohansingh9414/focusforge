package com.rohansingh.focusforge.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rohansingh.focusforge.FocusForgeApplication
import com.rohansingh.focusforge.domain.models.StatisticsUiState
import com.rohansingh.focusforge.domain.models.TimePeriod
import com.rohansingh.focusforge.ui.stats.components.CompletionsTrendChart
import com.rohansingh.focusforge.ui.stats.components.CreditsTrendChart
import com.rohansingh.focusforge.ui.stats.components.GoalPerformanceSection
import com.rohansingh.focusforge.ui.stats.components.RewardRedemptionSection
import com.rohansingh.focusforge.ui.stats.components.ScreenTimeTrendChart
import com.rohansingh.focusforge.ui.stats.components.ScreenTimeUsageSection
import com.rohansingh.focusforge.ui.stats.components.StatsSummaryCard

@Composable
fun StatsScreen(
    viewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.Factory(
            (LocalContext.current.applicationContext as FocusForgeApplication).statisticsRepository
        )
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Title
        item {
            Text(
                text = "Statistics",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            )
        }

        // 2. Time Period Selector Tabs
        item {
            TimePeriodSelector(
                selectedPeriod = uiState.selectedPeriod,
                onPeriodSelected = { viewModel.selectPeriod(it) }
            )
        }

        if (uiState.isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            // 3. Summary Overview 2x2 Cards
            item {
                SummaryCardsGrid(uiState)
            }

            // 4. Gamification & Level Overview Card
            item {
                GamificationOverviewCard(uiState)
            }

            // 5. Native Charts
            item {
                CreditsTrendChart(dataPoints = uiState.economyStats.dailyCreditsTrend)
            }

            item {
                CompletionsTrendChart(dataPoints = uiState.goalsStats.dailyCompletionsTrend)
            }

            item {
                ScreenTimeTrendChart(dataPoints = uiState.screenTimeStats.dailyUsageTrend)
            }

            // 6. Detailed Performance Lists
            item {
                GoalPerformanceSection(goals = uiState.goalsStats.goalPerformance)
            }

            item {
                RewardRedemptionSection(rewards = uiState.rewardsStats.rewardBreakdown)
            }

            item {
                ScreenTimeUsageSection(appUsages = uiState.screenTimeStats.appUsageBreakdown)
            }
        }
    }
}

@Composable
private fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TimePeriod.values().forEach { period ->
                val isSelected = (period == selectedPeriod)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        )
                        .clickable { onPeriodSelected(period) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = period.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCardsGrid(uiState: StatisticsUiState) {
    val earnedColor = Color(0xFF4CAF50)
    val spentColor = Color(0xFFFF7043)
    val goalsColor = MaterialTheme.colorScheme.primary
    val focusColor = Color(0xFF8E24AA)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsSummaryCard(
                title = "Credits Earned",
                value = "+${"%.1f".format(uiState.economyStats.totalCreditsEarned)}",
                subtitle = "From ${uiState.goalsStats.totalCompletions} completions",
                icon = Icons.Default.Star,
                accentColor = earnedColor,
                modifier = Modifier.weight(1f)
            )

            StatsSummaryCard(
                title = "Credits Spent",
                value = "-${"%.1f".format(uiState.economyStats.totalCreditsSpent)}",
                subtitle = "From ${uiState.rewardsStats.totalRedemptionsCount} rewards",
                icon = Icons.Default.ThumbUp,
                accentColor = spentColor,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatsSummaryCard(
                title = "Goals Completed",
                value = "${uiState.goalsStats.totalCompletions}",
                subtitle = "${uiState.goalsStats.goalPerformance.size} distinct goals",
                icon = Icons.Default.CheckCircle,
                accentColor = goalsColor,
                modifier = Modifier.weight(1f)
            )

            StatsSummaryCard(
                title = "Focus Time",
                value = "${uiState.focusStats.totalFocusMinutes} min",
                subtitle = "${uiState.focusStats.completedSessionsCount} sessions completed",
                icon = Icons.Default.Face,
                accentColor = focusColor,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GamificationOverviewCard(uiState: StatisticsUiState) {
    val gamification = uiState.gamificationStats
    val levelInfo = gamification.levelInfo

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Level ${levelInfo.currentLevel}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${gamification.periodXpEarned} XP earned in period • ${gamification.totalLifetimeXp} lifetime XP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }

                if (gamification.topStreak > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "🔥 ${gamification.topStreak}d streak",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { levelInfo.progressPercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${levelInfo.currentLevelXpProgress} / ${levelInfo.xpRequiredForNextLevel} XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )

                Text(
                    text = "Next: Level ${levelInfo.currentLevel + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
