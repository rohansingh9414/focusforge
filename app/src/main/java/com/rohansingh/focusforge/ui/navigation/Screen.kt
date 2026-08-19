package com.rohansingh.focusforge.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Type-safe route definitions for all navigation destinations.
 * All route strings are defined here — no raw string literals elsewhere.
 */
sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Goals : Screen("goals", "Goals", Icons.Default.Star)
    data object Rewards : Screen("rewards", "Rewards", Icons.Default.ShoppingCart)
    data object Restrictions : Screen("restrictions", "Restrictions", Icons.Default.Lock)
    data object Stats : Screen("stats", "Stats", Icons.Default.DateRange)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    companion object {
        val all = listOf(Home, Goals, Rewards, Restrictions, Stats, Settings)
    }
}
