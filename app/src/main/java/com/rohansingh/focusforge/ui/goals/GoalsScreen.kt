package com.rohansingh.focusforge.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rohansingh.focusforge.data.entities.GoalStreak
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.domain.gamification.GamificationConfig
import com.rohansingh.focusforge.domain.managers.FocusSessionManager
import com.rohansingh.focusforge.domain.managers.GoalManager
import kotlin.math.min

@Composable
fun GoalsScreen(
    goalRepository: GoalRepository,
    goalManager: GoalManager,
    focusSessionManager: FocusSessionManager,
    viewModel: GoalsViewModel = viewModel(
        factory = GoalsViewModel.Factory(goalRepository, goalManager, focusSessionManager)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddGoalDialog() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Goal")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.goals.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No goals set yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to create your first goal and earn credits!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Goals",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    // Active Focus Session Countdown Card
                    if (uiState.activeFocusSession != null) {
                        item {
                            ActiveFocusSessionCard(
                                session = uiState.activeFocusSession!!
                            )
                        }
                    }

                    items(uiState.goals, key = { it.id }) { goal ->
                        val isFocusEligible = viewModel.isGoalFocusEligible(goal)
                        val isGoalLockedInSession = uiState.activeFocusSession?.goalId == goal.id
                        val streak = uiState.streaks[goal.id]

                        GoalItemCard(
                            goal = goal,
                            streak = streak,
                            isFocusEligible = isFocusEligible,
                            isLockedInSession = isGoalLockedInSession,
                            onStartFocusClick = { viewModel.openStartFocusDialog(goal) },
                            onCompleteClick = { viewModel.openCompleteGoalDialog(goal) },
                            onEditClick = { viewModel.openEditGoalDialog(goal) },
                            onDeleteClick = { viewModel.deleteGoal(goal) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    if (uiState.isAddEditGoalDialogOpen) {
        AddEditGoalDialog(
            goal = uiState.editingGoal,
            onDismiss = { viewModel.closeAddEditGoalDialog() },
            onSave = { id, title, unit, rate, cap, recurring ->
                viewModel.saveGoal(id, title, unit, rate, cap, recurring)
            }
        )
    }

    if (uiState.isCompleteGoalDialogOpen && uiState.completingGoal != null) {
        CompleteGoalDialog(
            goal = uiState.completingGoal!!,
            onDismiss = { viewModel.closeCompleteGoalDialog() },
            onConfirm = { amount ->
                viewModel.completeGoal(uiState.completingGoal!!, amount)
            }
        )
    }

    if (uiState.isStartFocusDialogOpen && uiState.focusGoal != null) {
        StartFocusSessionDialog(
            goal = uiState.focusGoal!!,
            onDismiss = { viewModel.closeStartFocusDialog() },
            onStart = { durationMinutes ->
                viewModel.startFocusSession(uiState.focusGoal!!, durationMinutes)
            }
        )
    }
}

@Composable
private fun GoalItemCard(
    goal: GoalTemplate,
    streak: GoalStreak?,
    isFocusEligible: Boolean,
    isLockedInSession: Boolean,
    onStartFocusClick: () -> Unit,
    onCompleteClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(
                        onClick = onEditClick,
                        enabled = !isLockedInSession
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Goal")
                    }
                    IconButton(
                        onClick = onDeleteClick,
                        enabled = !isLockedInSession
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Goal")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Streak Badge
            if (streak != null && streak.currentStreak > 0) {
                val bonusFraction = min(
                    streak.currentStreak * GamificationConfig.STREAK_BONUS_PER_DAY,
                    GamificationConfig.MAX_STREAK_BONUS
                )
                val bonusPercent = (bonusFraction * 100).toInt()
                val bonusText = if (bonusPercent > 0) " (+${bonusPercent}% bonus)" else ""

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔥 ${streak.currentStreak} day streak$bonusText",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                text = "Reward: +${goal.creditRate} credits / ${goal.unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            val capText = if (goal.dailyCap > 0.0) "Daily cap: ${goal.dailyCap} credits" else "No daily cap"
            val recurringText = if (goal.recurring) "Recurring" else "One-off"
            Text(
                text = "$capText • $recurringText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isFocusEligible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartFocusClick,
                        modifier = Modifier.weight(1.5f),
                        enabled = !isLockedInSession
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Start Focus")
                    }

                    OutlinedButton(
                        onClick = onCompleteClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Manual")
                    }
                }
            } else {
                Button(
                    onClick = onCompleteClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Complete")
                }
            }
        }
    }
}

@Composable
private fun AddEditGoalDialog(
    goal: GoalTemplate?,
    onDismiss: () -> Unit,
    onSave: (id: Long, title: String, unit: String, creditRate: Double, dailyCap: Double, recurring: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(goal?.title ?: "") }
    var unit by remember { mutableStateOf(goal?.unit ?: "") }
    var creditRateStr by remember { mutableStateOf(goal?.creditRate?.toString() ?: "1.0") }
    var dailyCapStr by remember { mutableStateOf(if (goal != null && goal.dailyCap > 0.0) goal.dailyCap.toString() else "") }
    var recurring by remember { mutableStateOf(goal?.recurring ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) "New Goal" else "Edit Goal") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Read Book, Run, Study") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (use 'minutes' or 'hours' for Focus Mode)") },
                    placeholder = { Text("e.g. minutes, hours, pages, km") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = creditRateStr,
                    onValueChange = { creditRateStr = it },
                    label = { Text("Credit Rate (credits per unit)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = dailyCapStr,
                    onValueChange = { dailyCapStr = it },
                    label = { Text("Daily Cap (0 or empty = uncapped)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = recurring,
                        onCheckedChange = { recurring = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recurring daily goal")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rate = creditRateStr.toDoubleOrNull() ?: 1.0
                    val cap = dailyCapStr.toDoubleOrNull() ?: 0.0
                    onSave(goal?.id ?: 0L, title, unit, rate, cap, recurring)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CompleteGoalDialog(
    goal: GoalTemplate,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double) -> Unit
) {
    var amountStr by remember { mutableStateOf("1.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete Goal: ${goal.title}") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Enter the amount completed in ${goal.unit}:",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (${goal.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: 0.0
                    if (amount > 0.0) {
                        onConfirm(amount)
                    }
                }
            ) {
                Text("Complete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
