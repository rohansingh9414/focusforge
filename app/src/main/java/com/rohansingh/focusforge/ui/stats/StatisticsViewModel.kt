package com.rohansingh.focusforge.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rohansingh.focusforge.data.repository.StatisticsRepository
import com.rohansingh.focusforge.domain.models.EconomyStats
import com.rohansingh.focusforge.domain.models.FocusOverviewStats
import com.rohansingh.focusforge.domain.models.GamificationOverviewStats
import com.rohansingh.focusforge.domain.models.GoalsOverviewStats
import com.rohansingh.focusforge.domain.models.RewardsOverviewStats
import com.rohansingh.focusforge.domain.models.ScreenTimeOverviewStats
import com.rohansingh.focusforge.domain.models.StatisticsUiState
import com.rohansingh.focusforge.domain.models.TimePeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

/**
 * ViewModel for the Statistics Dashboard.
 * Manages the selected time period and coordinates read-only analytical aggregations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModel(
    private val statisticsRepository: StatisticsRepository,
    private val calendarProvider: () -> Calendar = { Calendar.getInstance() }
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TimePeriod.LAST_7_DAYS)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod

    val uiState: StateFlow<StatisticsUiState> = _selectedPeriod.flatMapLatest { period ->
        val range = StatisticsRepository.calculateTimeRange(period, calendarProvider())

        val economyFlow = statisticsRepository.getEconomyStats(period, range)
        val goalsFlow = statisticsRepository.getGoalsStats(period, range)
        val gamificationFlow = statisticsRepository.getGamificationStats(range)
        val focusFlow = statisticsRepository.getFocusStats(range)
        val rewardsFlow = statisticsRepository.getRewardsStats(range)
        val screenTimeFlow = statisticsRepository.getScreenTimeStats(period, range)

        val part1 = combine(economyFlow, goalsFlow, gamificationFlow) { economy, goals, gamification ->
            Triple(economy, goals, gamification)
        }
        val part2 = combine(focusFlow, rewardsFlow, screenTimeFlow) { focus, rewards, screenTime ->
            Triple(focus, rewards, screenTime)
        }

        combine(part1, part2) { (economy, goals, gamification), (focus, rewards, screenTime) ->
            StatisticsUiState(
                selectedPeriod = period,
                isLoading = false,
                economyStats = economy,
                goalsStats = goals,
                gamificationStats = gamification,
                focusStats = focus,
                rewardsStats = rewards,
                screenTimeStats = screenTime
            )
        }

    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(isLoading = true)
    )

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    class Factory(
        private val statisticsRepository: StatisticsRepository,
        private val calendarProvider: () -> Calendar = { Calendar.getInstance() }
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
                return StatisticsViewModel(statisticsRepository, calendarProvider) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
