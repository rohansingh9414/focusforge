package com.rohansingh.focusforge.data.repository

import com.rohansingh.focusforge.data.dao.RestrictedAppDao
import com.rohansingh.focusforge.data.entities.RestrictedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestrictedAppRepositoryTest {

    private lateinit var fakeDao: FakeRestrictedAppDao
    private lateinit var repository: RestrictedAppRepository

    @Before
    fun setUp() {
        fakeDao = FakeRestrictedAppDao()
        repository = RestrictedAppRepository(fakeDao)
    }

    @Test
    fun testSetAppRestricted_addsAndEnables() = runBlocking {
        repository.setAppRestricted("com.instagram.android", "Instagram", true)

        assertTrue(repository.isAppRestricted("com.instagram.android"))
        val active = repository.activeRestrictedPackageNames.first()
        assertTrue(active.contains("com.instagram.android"))
    }

    @Test
    fun testSetAppRestricted_disabledState() = runBlocking {
        repository.setAppRestricted("com.instagram.android", "Instagram", true)
        repository.setAppRestricted("com.instagram.android", "Instagram", false)

        assertFalse(repository.isAppRestricted("com.instagram.android"))
        val active = repository.activeRestrictedPackageNames.first()
        assertFalse(active.contains("com.instagram.android"))
    }

    @Test
    fun testRemoveRestrictedApp() = runBlocking {
        repository.setAppRestricted("com.twitter.android", "Twitter", true)
        assertTrue(repository.isAppRestricted("com.twitter.android"))

        repository.removeRestrictedApp("com.twitter.android")
        assertFalse(repository.isAppRestricted("com.twitter.android"))
    }

    private class FakeRestrictedAppDao : RestrictedAppDao {
        private val list = mutableListOf<RestrictedApp>()

        override fun getAllRestrictedApps(): Flow<List<RestrictedApp>> = flow {
            emit(list.toList())
        }

        override fun getActiveRestrictedApps(): Flow<List<RestrictedApp>> = flow {
            emit(list.filter { it.isRestricted })
        }

        override suspend fun getRestrictedApp(packageName: String): RestrictedApp? =
            list.find { it.packageName == packageName }

        override fun observeRestrictedApp(packageName: String): Flow<RestrictedApp?> = flow {
            emit(list.find { it.packageName == packageName })
        }

        override suspend fun insertOrUpdate(app: RestrictedApp): Long {
            list.removeAll { it.packageName == app.packageName }
            list.add(app)
            return 1L
        }

        override suspend fun insertAll(apps: List<RestrictedApp>): List<Long> {
            apps.forEach { insertOrUpdate(it) }
            return apps.indices.map { it.toLong() }
        }

        override suspend fun update(app: RestrictedApp): Int {
            list.removeAll { it.packageName == app.packageName }
            list.add(app)
            return 1
        }

        override suspend fun setRestrictedState(packageName: String, isRestricted: Boolean): Int {
            val app = list.find { it.packageName == packageName }
            if (app != null) {
                insertOrUpdate(app.copy(isRestricted = isRestricted))
                return 1
            }
            return 0
        }

        override suspend fun delete(app: RestrictedApp): Int {
            val removed = list.removeAll { it.packageName == app.packageName }
            return if (removed) 1 else 0
        }

        override suspend fun deleteByPackageName(packageName: String): Int {
            val removed = list.removeAll { it.packageName == packageName }
            return if (removed) 1 else 0
        }
    }
}
