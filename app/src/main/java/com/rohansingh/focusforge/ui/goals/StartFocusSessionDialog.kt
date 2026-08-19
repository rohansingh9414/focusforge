package com.rohansingh.focusforge.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rohansingh.focusforge.data.entities.GoalTemplate
import com.rohansingh.focusforge.domain.managers.FocusSessionManager

@Composable
fun StartFocusSessionDialog(
    goal: GoalTemplate,
    onDismiss: () -> Unit,
    onStart: (durationMinutes: Int) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(25) }
    var customMinutesStr by remember { mutableStateOf("") }
    var isCustom by remember { mutableStateOf(false) }

    val duration = if (isCustom) {
        customMinutesStr.toIntOrNull() ?: 0
    } else {
        selectedMinutes
    }

    val unit = goal.unit.trim().lowercase()
    val isHourUnit = unit in FocusSessionManager.HOUR_UNITS
    val amount = if (isHourUnit) duration / 60.0 else duration.toDouble()
    val estCredits = amount * goal.creditRate

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Focus Session") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Goal: ${goal.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Select session duration:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 25, 45, 60).forEach { mins ->
                        FilterChip(
                            selected = !isCustom && selectedMinutes == mins,
                            onClick = {
                                isCustom = false
                                selectedMinutes = mins
                            },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                OutlinedTextField(
                    value = customMinutesStr,
                    onValueChange = {
                        customMinutesStr = it
                        isCustom = true
                    },
                    label = { Text("Custom duration (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Reward: +${String.format("%.1f", estCredits)} credits",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Selected restricted apps will be locked until the timer reaches 00:00.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (duration > 0) {
                        onStart(duration)
                    }
                },
                enabled = duration > 0
            ) {
                Text("Start Focus")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
