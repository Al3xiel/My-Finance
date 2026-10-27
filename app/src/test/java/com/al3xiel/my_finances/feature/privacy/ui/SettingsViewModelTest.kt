package com.al3xiel.my_finances.feature.privacy.ui

import com.al3xiel.my_finances.feature.privacy.FakeThemeRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val repository = FakeThemeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun followsTheSystemByDefault() = runTest {
        assertEquals(ThemeMode.SYSTEM, SettingsViewModel(repository).uiState.value.themeMode)
    }

    @Test
    fun choosingAThemePersistsItAndUpdatesTheState() = runTest {
        val vm = SettingsViewModel(repository)
        vm.onAction(SettingsUiAction.SetThemeMode(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, repository.state.value)
        assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
    }
}
