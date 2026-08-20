package com.rohansingh.focusforge.ui.blocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.rohansingh.focusforge.FocusForgeApplication
import com.rohansingh.focusforge.MainActivity
import com.rohansingh.focusforge.domain.models.BlockerReason
import com.rohansingh.focusforge.domain.models.ThemeMode
import com.rohansingh.focusforge.ui.theme.FocusForgeTheme

/**
 * Activity presented when a user attempts to access a restricted application
 * while screen-time balance is exhausted (0 minutes) or while a Focus Session is running.
 */
class BlockerActivity : ComponentActivity() {

    private var blockedPackage: String? = null
    private var blockerReason: BlockerReason = BlockerReason.REGULAR_SCREEN_TIME_EXHAUSTED
    private var goalTitle: String? = null
    private var remainingSeconds: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        extractIntentExtras(intent)

        val app = application as? FocusForgeApplication ?: FocusForgeApplication.instance
        val themeRepository = app.themeRepository

        setContent {
            val themeMode by themeRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            FocusForgeTheme(darkTheme = darkTheme) {
                BlockerScreen(
                    blockedPackageName = blockedPackage,
                    reason = blockerReason,
                    goalTitle = goalTitle,
                    remainingSeconds = remainingSeconds,
                    onReturnToApp = { returnToFocusForge() },
                    onGoToHomeScreen = { goToHomeScreen() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIntentExtras(intent)
    }

    private fun extractIntentExtras(intent: Intent?) {
        blockedPackage = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE)
        val reasonStr = intent?.getStringExtra(EXTRA_BLOCKER_REASON)
        blockerReason = if (reasonStr == BlockerReason.FOCUS_SESSION_ACTIVE.name) {
            BlockerReason.FOCUS_SESSION_ACTIVE
        } else {
            BlockerReason.REGULAR_SCREEN_TIME_EXHAUSTED
        }
        goalTitle = intent?.getStringExtra(EXTRA_GOAL_TITLE)
        remainingSeconds = intent?.getIntExtra(EXTRA_REMAINING_SECONDS, 0) ?: 0
    }

    private fun returnToFocusForge() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun goToHomeScreen() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCKER_REASON = "extra_blocker_reason"
        const val EXTRA_GOAL_TITLE = "extra_goal_title"
        const val EXTRA_REMAINING_SECONDS = "extra_remaining_seconds"
    }
}
