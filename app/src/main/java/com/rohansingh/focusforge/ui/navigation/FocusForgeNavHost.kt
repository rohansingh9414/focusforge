package com.rohansingh.focusforge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.RewardRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.BarterManager
import com.rohansingh.focusforge.domain.managers.GoalManager
import com.rohansingh.focusforge.domain.managers.RewardManager
import com.rohansingh.focusforge.ui.goals.GoalsScreen
import com.rohansingh.focusforge.ui.home.HomeScreen
import com.rohansingh.focusforge.ui.restrictions.RestrictionsScreen
import com.rohansingh.focusforge.ui.rewards.RewardsScreen
import com.rohansingh.focusforge.ui.settings.SettingsScreen
import com.rohansingh.focusforge.ui.stats.StatsScreen

@Composable
fun FocusForgeNavHost(
    navController: NavHostController,
    walletRepository: WalletRepository,
    goalRepository: GoalRepository,
    goalManager: GoalManager,
    rewardRepository: RewardRepository,
    rewardManager: RewardManager,
    exchangeConfigRepository: ExchangeConfigRepository,
    barterManager: BarterManager,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreen(walletRepository = walletRepository) }
        composable(Screen.Goals.route) {
            GoalsScreen(
                goalRepository = goalRepository,
                goalManager = goalManager
            )
        }
        composable(Screen.Rewards.route) {
            RewardsScreen(
                rewardRepository = rewardRepository,
                rewardManager = rewardManager,
                exchangeConfigRepository = exchangeConfigRepository
            )
        }
        composable(Screen.Restrictions.route) { RestrictionsScreen() }
        composable(Screen.Stats.route) { StatsScreen() }
        composable(Screen.Settings.route) {
            SettingsScreen(
                walletRepository = walletRepository,
                exchangeConfigRepository = exchangeConfigRepository,
                barterManager = barterManager
            )
        }
    }
}
