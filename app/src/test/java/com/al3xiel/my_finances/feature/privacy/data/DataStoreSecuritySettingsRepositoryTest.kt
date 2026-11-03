package com.al3xiel.my_finances.feature.privacy.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSecuritySettingsRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.newRepository(file: File = folder.newFile("settings.preferences_pb").also { it.delete() }) =
        DataStoreSecuritySettingsRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(StandardTestDispatcher(testScheduler))) { file },
        )

    @Test
    fun defaultsMatchTheDecisions() = runTest {
        assertEquals(
            SecuritySettings(lockEnabled = true, autoLockMinutes = 5, secureScreen = true, hideAmounts = false),
            newRepository().settings.first(),
        )
    }

    @Test
    fun persistsEachSetting() = runTest {
        val repository = newRepository()
        repository.setLockEnabled(false)
        repository.setAutoLockMinutes(15)
        repository.setSecureScreen(false)
        repository.setHideAmounts(true)
        assertEquals(SecuritySettings(false, 15, false, true), repository.settings.first())
    }

    @Test
    fun ignoresAnAutoLockOptionOutsideTheCatalog() = runTest {
        val repository = newRepository()
        repository.setAutoLockMinutes(7)
        assertEquals(5, repository.settings.first().autoLockMinutes)
    }

    @Test
    fun clearRestoresDefaults() = runTest {
        val repository = newRepository()
        repository.setHideAmounts(true)
        repository.clear()
        assertEquals(SecuritySettings(), repository.settings.first())
    }
}
