package com.rohansingh.focusforge.ui.restrictions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rohansingh.focusforge.data.repository.RestrictedAppRepository
import com.rohansingh.focusforge.domain.models.InstalledAppInfo
import com.rohansingh.focusforge.services.usage.AppDiscoveryService
import com.rohansingh.focusforge.services.usage.AppMonitoringService
import com.rohansingh.focusforge.services.usage.ForegroundAppDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RestrictionsUiState(
    val apps: List<InstalledAppInfo> = emptyList(),
    val filteredApps: List<InstalledAppInfo> = emptyList(),
    val searchQuery: String = "",
    val hasUsageAccess: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val restrictedCount: Int = 0,
    val isMonitoringActive: Boolean = false
)

class RestrictionsViewModel(
    private val context: Context,
    private val restrictedAppRepository: RestrictedAppRepository,
    private val appDiscoveryService: AppDiscoveryService = AppDiscoveryService(context),
    private val detector: ForegroundAppDetector = ForegroundAppDetector(context)
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val rawInstalledApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val hasUsageAccess = MutableStateFlow(detector.hasUsageAccessPermission())
    private val hasOverlayPermission = MutableStateFlow(detector.hasOverlayPermission())

    val uiState: StateFlow<RestrictionsUiState> = combine(
        combine(rawInstalledApps, restrictedAppRepository.allRestrictedApps, searchQuery) { apps, restricted, query ->
            Triple(apps, restricted, query)
        },
        combine(hasUsageAccess, hasOverlayPermission, AppMonitoringService.isRunning) { usage, overlay, isRunning ->
            Triple(usage, overlay, isRunning)
        }
    ) { (installed, restrictedEntities, query), (usagePermission, overlayPermission, isRunning) ->
        val restrictedMap = restrictedEntities.associate { it.packageName to it.isRestricted }

        val appsWithStatus = installed.map { app ->
            app.copy(isRestricted = restrictedMap[app.packageName] == true)
        }

        val filtered = if (query.isBlank()) {
            appsWithStatus
        } else {
            appsWithStatus.filter {
                it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }

        val restrictedCount = appsWithStatus.count { it.isRestricted }

        RestrictionsUiState(
            apps = appsWithStatus,
            filteredApps = filtered,
            searchQuery = query,
            hasUsageAccess = usagePermission,
            hasOverlayPermission = overlayPermission,
            restrictedCount = restrictedCount,
            isMonitoringActive = isRunning
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RestrictionsUiState()
    )

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            hasUsageAccess.value = detector.hasUsageAccessPermission()
            hasOverlayPermission.value = detector.hasOverlayPermission()
            val apps = appDiscoveryService.getInstalledLaunchableApps()
            rawInstalledApps.value = apps
        }
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun checkPermission() {
        hasUsageAccess.value = detector.hasUsageAccessPermission()
        hasOverlayPermission.value = detector.hasOverlayPermission()
    }

    fun getUsageAccessSettingsIntent() = detector.getUsageAccessSettingsIntent()

    fun getOverlaySettingsIntent() = detector.getOverlaySettingsIntent()

    fun toggleAppRestriction(packageName: String, appName: String, currentlyRestricted: Boolean) {
        viewModelScope.launch {
            val newRestrictedState = !currentlyRestricted
            if (newRestrictedState) {
                restrictedAppRepository.setAppRestricted(packageName, appName, true)
                // If service is not running and we just restricted an app, start monitoring service
                if (!AppMonitoringService.isRunning.value && detector.hasUsageAccessPermission()) {
                    AppMonitoringService.start(context)
                }
            } else {
                restrictedAppRepository.setAppRestricted(packageName, appName, false)
            }
        }
    }

    fun startService() {
        AppMonitoringService.start(context)
    }

    fun stopService() {
        AppMonitoringService.stop(context)
    }
}

class RestrictionsViewModelFactory(
    private val context: Context,
    private val restrictedAppRepository: RestrictedAppRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RestrictionsViewModel::class.java)) {
            return RestrictionsViewModel(context, restrictedAppRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
