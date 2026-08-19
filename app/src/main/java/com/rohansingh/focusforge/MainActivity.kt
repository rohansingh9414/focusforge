package com.rohansingh.focusforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.rohansingh.focusforge.data.database.AppDatabase
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.GoalManager
import com.rohansingh.focusforge.ui.navigation.BottomNavBar
import com.rohansingh.focusforge.ui.navigation.FocusForgeNavHost
import com.rohansingh.focusforge.ui.theme.FocusForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val walletRepository = WalletRepository(database.walletDao())
        val goalRepository = GoalRepository(database.goalTemplateDao(), database.goalLogDao())
        val goalManager = GoalManager(goalRepository, walletRepository)

        setContent {
            FocusForgeTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavBar(navController) }
                ) { innerPadding ->
                    FocusForgeNavHost(
                        navController = navController,
                        walletRepository = walletRepository,
                        goalRepository = goalRepository,
                        goalManager = goalManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}