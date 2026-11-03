package com.al3xiel.my_finances.feature.onboarding.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.feature.privacy.FakeSecuritySettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecuritySetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeSecuritySettingsRepository()
    private val authenticator = FakeAuthenticator()

    private fun viewModel() = SecuritySetupViewModel(settings, authenticator)

    @Test
    fun showsTheRealLockStateWithDefaultsOn() {
        val state = viewModel().uiState.value
        assertTrue(state.lockAvailable)
        assertTrue(state.lockEnabled)
        assertTrue(state.secureScreen)
        assertFalse(state.saved)
    }

    @Test
    fun withoutAnEnrolledCredentialTheLockIsNotActivatedAndTheUserIsWarned() {
        authenticator.availability = AuthAvailability.NO_CREDENTIAL_ENROLLED
        val vm = viewModel()

        assertFalse(vm.uiState.value.lockAvailable)
        assertFalse(vm.uiState.value.lockEnabled)

        vm.onAction(SecuritySetupUiAction.SetLock(true))
        assertFalse(vm.uiState.value.lockEnabled)

        vm.onAction(SecuritySetupUiAction.Save)
        assertFalse(settings.state.value.lockEnabled)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun savingWithDefaultsKeepsTheLockOn() {
        val vm = viewModel()
        vm.onAction(SecuritySetupUiAction.Save)

        assertTrue(settings.state.value.lockEnabled)
        assertTrue(settings.state.value.secureScreen)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun turningThingsOffPersistsOnSaveOnly() {
        val vm = viewModel()
        vm.onAction(SecuritySetupUiAction.SetLock(false))
        vm.onAction(SecuritySetupUiAction.SetSecureScreen(false))
        assertTrue(settings.state.value.lockEnabled)

        vm.onAction(SecuritySetupUiAction.Save)

        assertEquals(false, settings.state.value.lockEnabled)
        assertEquals(false, settings.state.value.secureScreen)
        assertTrue(vm.uiState.value.saved)
    }
}
