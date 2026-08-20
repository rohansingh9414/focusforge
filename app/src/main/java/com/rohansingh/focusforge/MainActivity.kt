package com.rohansingh.focusforge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.rohansingh.focusforge.domain.models.ThemeMode
import com.rohansingh.focusforge.services.usage.AppMonitoringService
import com.rohansingh.focusforge.ui.navigation.BottomNavBar
import com.rohansingh.focusforge.ui.navigation.FocusForgeNavHost
import com.rohansingh.focusforge.ui.theme.FocusForgeTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Permission result handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        val app = application as FocusForgeApplication
        val walletRepository = app.walletRepository
        val exchangeConfigRepository = app.exchangeConfigRepository
        val themeRepository = app.themeRepository
        val goalRepository = app.goalRepository
        val goalManager = app.goalManager
        val rewardRepository = app.rewardRepository
        val rewardManager = app.rewardManager
        val barterManager = app.barterManager
        val restrictedAppRepository = app.restrictedAppRepository
        val focusSessionManager = app.focusSessionManager

        // Start restriction monitoring service
        AppMonitoringService.start(applicationContext)

        setContent {
            val themeMode by themeRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            FocusForgeTheme(darkTheme = darkTheme) {
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
                        themeRepository = themeRepository,
                        barterManager = barterManager,
                        restrictedAppRepository = restrictedAppRepository,
                        focusSessionManager = focusSessionManager,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}