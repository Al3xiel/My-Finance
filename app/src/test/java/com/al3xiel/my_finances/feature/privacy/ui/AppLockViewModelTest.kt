package com.al3xiel.my_finances.feature.privacy.ui

import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.feature.privacy.FakeSecuritySettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {

    private class TestClock(var now: Long = 0) : MonotonicClock {
        override fun nowMillis() = now
    }

    private val minute = 60_000L
    private val prompt = AuthPrompt("Desbloquear")
    private val clock = TestClock()
    private val authenticator = FakeAuthenticator()
    private val settings = FakeSecuritySettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AppLockViewModel(settings, authenticator, clock)

    private fun AppLockViewModel.unlock() = onAction(AppLockUiAction.Unlock(prompt))

    @Test
    fun startsLockedWhenLockIsEnabled() = runTest {
        assertTrue(viewModel().uiState.value.isLocked)
    }

    @Test
    fun startsUnlockedWhenLockIsDisabled() = runTest {
        settings.setLockEnabled(false)
        assertFalse(viewModel().uiState.value.isLocked)
    }

    @Test
    fun successfulAuthenticationUnlocks() = runTest {
        val vm = viewModel()
        vm.unlock()
        assertFalse(vm.uiState.value.isLocked)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun cancellationStaysLockedWithoutMessage() = runTest {
        authenticator.result = AuthResult.Cancelled
        val vm = viewModel()
        vm.unlock()
        assertTrue(vm.uiState.value.isLocked)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun lockoutAndFailureShowAMessage() = runTest {
        val vm = viewModel()
        authenticator.result = AuthResult.LockedOut
        vm.unlock()
        assertEquals(AppLockMessage.LOCKED_OUT, vm.uiState.value.message)
        authenticator.result = AuthResult.Failed
        vm.unlock()
        assertEquals(AppLockMessage.FAILED, vm.uiState.value.message)
        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun withoutCredentialsItStaysLockedAndDoesNotPrompt() = runTest {
        authenticator.availability = AuthAvailability.NO_CREDENTIAL_ENROLLED
        val vm = viewModel()
        vm.unlock()
        assertEquals(0, authenticator.calls)
        assertEquals(AppLockMessage.NOT_AVAILABLE, vm.uiState.value.message)
        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun relocksAfterTheConfiguredTimeInBackground() = runTest {
        val vm = viewModel()
        vm.unlock()
        vm.onAction(AppLockUiAction.AppBackgrounded)
        clock.now = 5 * minute
        vm.onAction(AppLockUiAction.AppForegrounded)
        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun staysUnlockedOnAShortTrip() = runTest {
        val vm = viewModel()
        vm.unlock()
        vm.onAction(AppLockUiAction.AppBackgrounded)
        clock.now = minute
        vm.onAction(AppLockUiAction.AppForegrounded)
        assertFalse(vm.uiState.value.isLocked)
    }

    @Test
    fun foregroundWithoutPriorBackgroundIsIgnored() = runTest {
        val vm = viewModel()
        vm.unlock()
        vm.onAction(AppLockUiAction.AppForegrounded)
        assertFalse(vm.uiState.value.isLocked)
    }

    @Test
    fun disablingTheLockUnlocks() = runTest {
        val vm = viewModel()
        settings.setLockEnabled(false)
        assertFalse(vm.uiState.value.isLocked)
    }
}
