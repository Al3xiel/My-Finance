package com.al3xiel.my_finances.feature.privacy.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
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
class DataStoreThemeRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
    ) { folder.newFile("theme.preferences_pb").also { it.delete() } }

    @Test
    fun followsTheSystemByDefault() = runTest {
        assertEquals(ThemeMode.SYSTEM, DataStoreThemeRepository(dataStore()).themeMode.first())
    }

    @Test
    fun persistsEachMode() = runTest {
        val repository = DataStoreThemeRepository(dataStore())
        ThemeMode.entries.forEach { mode ->
            repository.setThemeMode(mode)
            assertEquals(mode, repository.themeMode.first())
        }
    }

    @Test
    fun anUnknownStoredValueFallsBackToTheSystem() = runTest {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("appearance.theme_mode")] = "SEPIA" }
        assertEquals(ThemeMode.SYSTEM, DataStoreThemeRepository(store).themeMode.first())
    }
}
