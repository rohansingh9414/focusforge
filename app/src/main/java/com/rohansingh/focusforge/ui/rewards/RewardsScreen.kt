package com.rohansingh.focusforge.ui.rewards

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rohansingh.focusforge.data.entities.RewardTemplate
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.RewardRepository
import com.rohansingh.focusforge.domain.managers.RewardManager
import com.rohansingh.focusforge.domain.models.PricingMode
import com.rohansingh.focusforge.domain.models.RewardType
import kotlinx.coroutines.flow.collectLatest

@Composable
fun RewardsScreen(
    rewardRepository: RewardRepository,
    rewardManager: RewardManager,
    exchangeConfigRepository: ExchangeConfigRepository,
    viewModel: RewardsViewModel = viewModel(
        factory = RewardsViewModel.Factory(rewardRepository, rewardManager, exchangeConfigRepository)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.snackbarMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddRewardDialog() }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Reward")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.rewards.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No rewards configured yet.\nTap + to create your first reward.",
                        style = MaterialTheme.typography.bodyLarge,
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
                            text = "Rewards",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    items(uiState.rewards, key = { it.template.id }) { item ->
                        RewardItemCard(
                            item = item,
                            onRedeemClick = { viewModel.openRedeemDialog(item.template) },
                            onEditClick = { viewModel.openEditRewardDialog(item.template) },
                            onDeleteClick = { viewModel.deleteReward(item.template) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }
    }

    if (uiState.isAddEditRewardDialogOpen) {
        AddEditRewardDialog(
            reward = uiState.editingReward,
            creditsPerRupee = uiState.exchangeConfig.creditsPerRupee,
            onDismiss = { viewModel.closeAddEditRewardDialog() },
            onSave = { id, title, unit, rewardType, pricingMode, rupeeCost, creditRate, isActive ->
                viewModel.saveReward(id, title, unit, rewardType, pricingMode, rupeeCost, creditRate, isActive)
            }
        )
    }

    if (uiState.isRedeemDialogOpen && uiState.redeemingReward != null) {
        val redeemingTemplate = uiState.redeemingReward!!
        val effectiveRate = rewardManager.calculateEffectiveCreditRate(
            redeemingTemplate,
            uiState.exchangeConfig.creditsPerRupee
        )
        RedeemRewardDialog(
            reward = redeemingTemplate,
            effectiveCreditRate = effectiveRate,
            onDismiss = { viewModel.closeRedeemDialog() },
            onConfirm = { units ->
                viewModel.redeemReward(redeemingTemplate, units)
            }
        )
    }
}

@Composable
private fun RewardItemCard(
    item: RewardUiItem,
    onRedeemClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reward = item.template
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
                    text = reward.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Reward")
                    }
                    IconButton(onClick = onDeleteClick) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Reward")
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val priceDetail = if (reward.pricingMode == PricingMode.AUTO) {
                "${String.format("%.1f", item.effectiveCreditRate)} credits / ${reward.unit} (AUTO: ₹${String.format("%.2f", reward.rupeeCost)})"
            } else {
                "${String.format("%.1f", item.effectiveCreditRate)} credits / ${reward.unit} (MANUAL)"
            }

            Text(
                text = "Cost: $priceDetail",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(2.dp))

            val typeText = if (reward.rewardType == RewardType.SCREEN_TIME) "Screen Time" else "Custom Reward"
            Text(
                text = "Type: $typeText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRedeemClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Redeem")
            }
        }
    }
}

@Composable
private fun AddEditRewardDialog(
    reward: RewardTemplate?,
    creditsPerRupee: Double,
    onDismiss: () -> Unit,
    onSave: (id: Long, title: String, unit: String, rewardType: RewardType, pricingMode: PricingMode, rupeeCost: Double, creditRate: Double, isActive: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(reward?.title ?: "") }
    var unit by remember { mutableStateOf(reward?.unit ?: "") }
    var rewardType by remember { mutableStateOf(reward?.rewardType ?: RewardType.CUSTOM) }
    var pricingMode by remember { mutableStateOf(reward?.pricingMode ?: PricingMode.AUTO) }
    var rupeeCostStr by remember { mutableStateOf(if (reward != null && reward.rupeeCost > 0.0) reward.rupeeCost.toString() else "50.0") }
    var creditRateStr by remember { mutableStateOf(if (reward != null && reward.creditRate > 0.0) reward.creditRate.toString() else "50.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reward == null) "New Reward" else "Edit Reward") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Ice Cream, Gaming Session") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit") },
                    placeholder = { Text("e.g. scoop, minutes, item") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Reward Type",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = rewardType == RewardType.CUSTOM,
                            onClick = { rewardType = RewardType.CUSTOM }
                        )
                        Text("Custom / Real-world")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = rewardType == RewardType.SCREEN_TIME,
                            onClick = {
                                rewardType = RewardType.SCREEN_TIME
                                if (unit.isBlank()) unit = "minutes"
                            }
                        )
                        Text("Screen Time (+minutes)")
                    }
                }

                Text(
                    text = "Pricing Mode",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pricingMode == PricingMode.AUTO,
                            onClick = { pricingMode = PricingMode.AUTO }
                        )
                        Text("AUTO (Rupee-based)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = pricingMode == PricingMode.MANUAL,
                            onClick = { pricingMode = PricingMode.MANUAL }
                        )
                        Text("MANUAL (Fixed Credits)")
                    }
                }

                if (pricingMode == PricingMode.AUTO) {
                    OutlinedTextField(
                        value = rupeeCostStr,
                        onValueChange = { rupeeCostStr = it },
                        label = { Text("Rupee Cost (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val rupee = rupeeCostStr.toDoubleOrNull() ?: 0.0
                    val autoCredits = rupee * creditsPerRupee
                    Text(
                        text = "Live preview: ${String.format("%.1f", autoCredits)} credits / $unit (₹${String.format("%.2f", rupee)} × $creditsPerRupee)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    OutlinedTextField(
                        value = creditRateStr,
                        onValueChange = { creditRateStr = it },
                        label = { Text("Credit Rate (credits per unit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rupee = rupeeCostStr.toDoubleOrNull() ?: 0.0
                    val rate = creditRateStr.toDoubleOrNull() ?: 0.0
                    onSave(reward?.id ?: 0L, title, unit, rewardType, pricingMode, rupee, rate, true)
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
private fun RedeemRewardDialog(
    reward: RewardTemplate,
    effectiveCreditRate: Double,
    onDismiss: () -> Unit,
    onConfirm: (units: Double) -> Unit
) {
    var unitsStr by remember { mutableStateOf("1.0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Redeem: ${reward.title}") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Rate: ${String.format("%.1f", effectiveCreditRate)} credits / ${reward.unit}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = unitsStr,
                    onValueChange = { unitsStr = it },
                    label = { Text("Units (${reward.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val units = unitsStr.toDoubleOrNull() ?: 0.0
                val totalCost = units * effectiveCreditRate
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total Cost: ${String.format("%.1f", totalCost)} credits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (reward.rewardType == RewardType.SCREEN_TIME) {
                    Text(
                        text = "Grants +${units.toInt()} minutes to screen time balance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val units = unitsStr.toDoubleOrNull() ?: 0.0
                    if (units > 0.0) {
                        onConfirm(units)
                    }
                }
            ) {
                Text("Redeem")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
