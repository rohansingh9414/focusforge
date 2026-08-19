package com.rohansingh.focusforge.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.rohansingh.focusforge.ui.goals.GoalsScreen
import com.rohansingh.focusforge.ui.home.HomeScreen
import com.rohansingh.focusforge.ui.restrictions.RestrictionsScreen
import com.rohansingh.focusforge.ui.rewards.RewardsScreen
import com.rohansingh.focusforge.ui.settings.SettingsScreen
import com.rohansingh.focusforge.ui.stats.StatsScreen

@Composable
fun FocusForgeNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) { HomeScreen() }
        composable(Screen.Goals.route) { GoalsScreen() }
        composable(Screen.Rewards.route) { RewardsScreen() }
        composable(Screen.Restrictions.route) { RestrictionsScreen() }
        composable(Screen.Stats.route) { StatsScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}
