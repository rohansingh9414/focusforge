package com.rohansingh.focusforge.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rohansingh.focusforge.FocusForgeApplication
import com.rohansingh.focusforge.data.entities.GoalStreak
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.domain.managers.FocusSessionManager
import com.rohansingh.focusforge.domain.managers.GoalManager
import com.rohansingh.focusforge.domain.models.ActiveFocusSession
import com.rohansingh.focusforge.services.usage.AppMonitoringService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalsUiState(
    val goals: List<GoalTemplate> = emptyList(),
    val streaks: Map<Long, GoalStreak> = emptyMap(),
    val activeFocusSession: ActiveFocusSession? = null,
    val isAddEditGoalDialogOpen: Boolean = false,
    val editingGoal: GoalTemplate? = null,
    val isCompleteGoalDialogOpen: Boolean = false,
    val completingGoal: GoalTemplate? = null,
    val isStartFocusDialogOpen: Boolean = false,
    val focusGoal: GoalTemplate? = null,
    val isLoading: Boolean = false
)

class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val goalManager: GoalManager,
    private val focusSessionManager: FocusSessionManager
) : ViewModel() {

    private val _isAddEditGoalDialogOpen = MutableStateFlow(false)
    private val _editingGoal = MutableStateFlow<GoalTemplate?>(null)
    private val _isCompleteGoalDialogOpen = MutableStateFlow(false)
    private val _completingGoal = MutableStateFlow<GoalTemplate?>(null)
    private val _isStartFocusDialogOpen = MutableStateFlow(false)
    private val _focusGoal = MutableStateFlow<GoalTemplate?>(null)

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val uiState: StateFlow<GoalsUiState> = combine(
        goalRepository.allGoalTemplates,
        goalRepository.allGoalStreaks,
        focusSessionManager.activeSession,
        _isAddEditGoalDialogOpen,
        _editingGoal,
        _isCompleteGoalDialogOpen,
        _completingGoal,
        _isStartFocusDialogOpen,
        _focusGoal
    ) { params ->
        val goals = params[0] as List<GoalTemplate>
        val streaksList = params[1] as List<GoalStreak>
        val activeFocus = params[2] as ActiveFocusSession?
        val isAddEditOpen = params[3] as Boolean
        val editing = params[4] as GoalTemplate?
        val isCompleteOpen = params[5] as Boolean
        val completing = params[6] as GoalTemplate?
        val isStartFocusOpen = params[7] as Boolean
        val focusGoal = params[8] as GoalTemplate?

        val streaksMap = streaksList.associateBy { it.goalTemplateId }

        GoalsUiState(
            goals = goals,
            streaks = streaksMap,
            activeFocusSession = activeFocus,
            isAddEditGoalDialogOpen = isAddEditOpen,
            editingGoal = editing,
            isCompleteGoalDialogOpen = isCompleteOpen,
            completingGoal = completing,
            isStartFocusDialogOpen = isStartFocusOpen,
            focusGoal = focusGoal,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalsUiState(isLoading = true)
    )

    fun isGoalFocusEligible(goal: GoalTemplate): Boolean =
        focusSessionManager.isGoalFocusEligible(goal)

    fun openAddGoalDialog() {
        _editingGoal.value = null
        _isAddEditGoalDialogOpen.value = true
    }

    fun openEditGoalDialog(goal: GoalTemplate) {
        if (uiState.value.activeFocusSession?.goalId == goal.id) {
            viewModelScope.launch {
                _snackbarMessage.emit("Cannot edit goal while an active focus session is running for it")
            }
            return
        }
        _editingGoal.value = goal
        _isAddEditGoalDialogOpen.value = true
    }

    fun closeAddEditGoalDialog() {
        _isAddEditGoalDialogOpen.value = false
        _editingGoal.value = null
    }

    fun openCompleteGoalDialog(goal: GoalTemplate) {
        _completingGoal.value = goal
        _isCompleteGoalDialogOpen.value = true
    }

    fun closeCompleteGoalDialog() {
        _isCompleteGoalDialogOpen.value = false
        _completingGoal.value = null
    }

    fun openStartFocusDialog(goal: GoalTemplate) {
        if (uiState.value.activeFocusSession != null) {
            viewModelScope.launch {
                _snackbarMessage.emit("A Focus Session is already running. Please wait for it to complete.")
            }
            return
        }
        _focusGoal.value = goal
        _isStartFocusDialogOpen.value = true
    }

    fun closeStartFocusDialog() {
        _isStartFocusDialogOpen.value = false
        _focusGoal.value = null
    }

    fun startFocusSession(goal: GoalTemplate, durationMinutes: Int) {
        viewModelScope.launch {
            try {
                AppMonitoringService.start(FocusForgeApplication.instance)
            } catch (_: Exception) {
                // Headless test fallback
            }
            val result = focusSessionManager.startSession(goal, durationMinutes)
            result.onSuccess {
                _snackbarMessage.emit("Focus session started for ${durationMinutes}m! Restricted apps are locked.")
                closeStartFocusDialog()
            }.onFailure { error ->
                _snackbarMessage.emit(error.message ?: "Failed to start focus session")
            }
        }
    }

    fun saveGoal(
        id: Long = 0,
        title: String,
        unit: String,
        creditRate: Double,
        dailyCap: Double,
        recurring: Boolean
    ) {
        viewModelScope.launch {
            if (title.isBlank() || unit.isBlank() || creditRate <= 0.0) {
                _snackbarMessage.emit("Please enter valid title, unit, and positive credit rate")
                return@launch
            }

            if (id != 0L && uiState.value.activeFocusSession?.goalId == id) {
                _snackbarMessage.emit("Cannot edit goal while an active focus session is running for it")
                return@launch
            }

            val goal = GoalTemplate(
                id = id,
                title = title.trim(),
                unit = unit.trim(),
                creditRate = creditRate,
                dailyCap = if (dailyCap < 0.0) 0.0 else dailyCap,
                recurring = recurring
            )

            if (id == 0L) {
                goalRepository.insertGoalTemplate(goal)
                _snackbarMessage.emit("Goal created")
            } else {
                goalRepository.updateGoalTemplate(goal)
                _snackbarMessage.emit("Goal updated")
            }
            closeAddEditGoalDialog()
        }
    }

    fun deleteGoal(goal: GoalTemplate) {
        viewModelScope.launch {
            if (uiState.value.activeFocusSession?.goalId == goal.id) {
                _snackbarMessage.emit("Cannot delete goal while an active focus session is running for it")
                return@launch
            }
            goalRepository.deleteGoalTemplate(goal)
            _snackbarMessage.emit("Goal deleted")
        }
    }

    fun completeGoal(goal: GoalTemplate, amount: Double) {
        viewModelScope.launch {
            val result = goalManager.completeGoal(goal, amount)
            result.onSuccess { earned ->
                if (earned > 0.0) {
                    val xp = Math.round(earned * 10.0)
                    _snackbarMessage.emit("Earned +${String.format("%.1f", earned)} credits and +$xp XP!")
                } else {
                    _snackbarMessage.emit("Daily cap reached. 0 credits earned.")
                }
                closeCompleteGoalDialog()
            }.onFailure { error ->
                _snackbarMessage.emit(error.message ?: "Failed to complete goal")
            }
        }
    }

    class Factory(
        private val goalRepository: GoalRepository,
        private val goalManager: GoalManager,
        private val focusSessionManager: FocusSessionManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GoalsViewModel::class.java)) {
                return GoalsViewModel(goalRepository, goalManager, focusSessionManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
