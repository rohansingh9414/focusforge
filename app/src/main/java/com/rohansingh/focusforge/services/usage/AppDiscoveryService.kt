package com.rohansingh.focusforge.services.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rohansingh.focusforge.domain.models.InstalledAppInfo

/**
 * Service to discover user-launchable applications installed on the device.
 */
class AppDiscoveryService(private val context: Context) {

    /**
     * Queries launchable applications using the standard MAIN/LAUNCHER intent query.
     * Excludes FocusForge itself.
     */
    fun getInstalledLaunchableApps(): List<InstalledAppInfo> {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                mainIntent,
                PackageManager.ResolveInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(mainIntent, 0)
        }

        val selfPackage = context.packageName

        return resolveInfos
            .asSequence()
            .map { it.activityInfo }
            .filter { it != null && it.packageName != selfPackage }
            .distinctBy { it.packageName }
            .map { activityInfo ->
                val label = activityInfo.loadLabel(packageManager).toString()
                val appName = if (label.isNotBlank()) label else activityInfo.packageName
                InstalledAppInfo(
                    packageName = activityInfo.packageName,
                    appName = appName
                )
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }
}
