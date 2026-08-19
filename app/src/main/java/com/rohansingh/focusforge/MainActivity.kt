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
import com.rohansingh.focusforge.data.repository.ExchangeConfigRepository
import com.rohansingh.focusforge.data.repository.GoalRepository
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.data.repository.RewardRepository
import com.rohansingh.focusforge.data.repository.WalletRepository
import com.rohansingh.focusforge.domain.managers.BarterManager
import com.rohansingh.focusforge.domain.managers.GoalManager
import com.rohansingh.focusforge.domain.managers.RewardManager
import com.rohansingh.focusforge.services.usage.AppMonitoringService
import com.rohansingh.focusforge.ui.navigation.BottomNavBar
import com.rohansingh.focusforge.ui.navigation.FocusForgeNavHost
import com.rohansingh.focusforge.ui.theme.FocusForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val walletRepository = WalletRepository(database.walletDao())
        val exchangeConfigRepository = ExchangeConfigRepository(applicationContext)
        val goalRepository = GoalRepository(database.goalTemplateDao(), database.goalLogDao())
        val goalManager = GoalManager(goalRepository, walletRepository)
        val rewardRepository = RewardRepository(database.rewardTemplateDao(), database.redemptionLogDao())
        val rewardManager = RewardManager(rewardRepository, walletRepository, exchangeConfigRepository)
        val barterManager = BarterManager(walletRepository, exchangeConfigRepository)
        val restrictedAppRepository = RestrictedAppRepository(database.restrictedAppDao())

        // Start restriction monitoring service
        AppMonitoringService.start(applicationContext)

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
                        rewardRepository = rewardRepository,
                        rewardManager = rewardManager,
                        exchangeConfigRepository = exchangeConfigRepository,
                        barterManager = barterManager,
                        restrictedAppRepository = restrictedAppRepository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}