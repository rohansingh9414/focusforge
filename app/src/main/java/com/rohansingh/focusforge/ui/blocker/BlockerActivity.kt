package com.rohansingh.focusforge.ui.blocker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rohansingh.focusforge.MainActivity
import com.rohansingh.focusforge.ui.theme.FocusForgeTheme

/**
 * Activity presented when a user attempts to access a restricted application
 * while screen-time balance is exhausted (0 minutes).
 */
class BlockerActivity : ComponentActivity() {

    private var blockedPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)

        setContent {
            FocusForgeTheme {
                BlockerScreen(
                    blockedPackageName = blockedPackage,
                    onReturnToApp = { returnToFocusForge() },
                    onGoToHomeScreen = { goToHomeScreen() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE)
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
    }
}
