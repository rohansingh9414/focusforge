package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.data.dao.RestrictedAppDao
import com.rohansingh.focusforge.data.entities.RestrictedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository providing access to restricted applications data.
 */
open class RestrictedAppRepository(
    private val restrictedAppDao: RestrictedAppDao
) {
    val allRestrictedApps: Flow<List<RestrictedApp>> =
        restrictedAppDao.getAllRestrictedApps()

    val activeRestrictedApps: Flow<List<RestrictedApp>> =
        restrictedAppDao.getActiveRestrictedApps()

    val activeRestrictedPackageNames: Flow<Set<String>> =
        activeRestrictedApps.map { list -> list.map { it.packageName }.toSet() }

    open suspend fun isAppRestricted(packageName: String): Boolean {
        val app = restrictedAppDao.getRestrictedApp(packageName)
        return app?.isRestricted == true
    }

    open suspend fun getAppName(packageName: String): String? {
        return restrictedAppDao.getRestrictedApp(packageName)?.appName
    }


    open suspend fun setAppRestricted(packageName: String, appName: String, isRestricted: Boolean) {
        restrictedAppDao.insertOrUpdate(
            RestrictedApp(
                packageName = packageName,
                appName = appName,
                isRestricted = isRestricted
            )
        )
    }

    open suspend fun removeRestrictedApp(packageName: String) {
        restrictedAppDao.deleteByPackageName(packageName)
    }
}
