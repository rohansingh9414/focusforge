package com.rohansingh.focusforge.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.domain.managers.GoalManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GoalsUiState(
    val goals: List<GoalTemplate> = emptyList(),
    val isAddEditGoalDialogOpen: Boolean = false,
    val editingGoal: GoalTemplate? = null,
    val isCompleteGoalDialogOpen: Boolean = false,
    val completingGoal: GoalTemplate? = null,
    val isLoading: Boolean = false
)

class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val goalManager: GoalManager
) : ViewModel() {

    private val _isAddEditGoalDialogOpen = MutableStateFlow(false)
    private val _editingGoal = MutableStateFlow<GoalTemplate?>(null)
    private val _isCompleteGoalDialogOpen = MutableStateFlow(false)
    private val _completingGoal = MutableStateFlow<GoalTemplate?>(null)

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    val uiState: StateFlow<GoalsUiState> = combine(
        goalRepository.allGoalTemplates,
        _isAddEditGoalDialogOpen,
        _editingGoal,
        _isCompleteGoalDialogOpen,
        _completingGoal
    ) { goals, isAddEditOpen, editing, isCompleteOpen, completing ->
        GoalsUiState(
            goals = goals,
            isAddEditGoalDialogOpen = isAddEditOpen,
            editingGoal = editing,
            isCompleteGoalDialogOpen = isCompleteOpen,
            completingGoal = completing,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalsUiState(isLoading = true)
    )

    fun openAddGoalDialog() {
        _editingGoal.value = null
        _isAddEditGoalDialogOpen.value = true
    }

    fun openEditGoalDialog(goal: GoalTemplate) {
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
            goalRepository.deleteGoalTemplate(goal)
            _snackbarMessage.emit("Goal deleted")
        }
    }

    fun completeGoal(goal: GoalTemplate, amount: Double) {
        viewModelScope.launch {
            val result = goalManager.completeGoal(goal, amount)
            result.onSuccess { earned ->
                if (earned > 0.0) {
                    _snackbarMessage.emit("Earned +${String.format("%.1f", earned)} credits!")
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
        private val goalManager: GoalManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GoalsViewModel::class.java)) {
                return GoalsViewModel(goalRepository, goalManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
