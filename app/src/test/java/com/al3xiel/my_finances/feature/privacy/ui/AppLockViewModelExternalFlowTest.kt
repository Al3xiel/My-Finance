package com.al3xiel.my_finances.feature.privacy.ui

import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.ExternalFlowGuard
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El selector de archivos del sistema manda la app a segundo plano: con la guardia abierta no cuenta para el re-bloqueo. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelExternalFlowTest {

    private class TestClock(var now: Long = 0) : MonotonicClock {
        override fun nowMillis() = now
    }

    private val minute = 60_000L
    private val clock = TestClock()
    private val guard = ExternalFlowGuard(clock)
    private val settings = FakeSecuritySettingsRepository()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun unlockedViewModel(): AppLockViewModel {
        val vm = AppLockViewModel(settings, FakeAuthenticator(), clock, guard)
        vm.onAction(AppLockUiAction.Unlock(AuthPrompt("Desbloquear")))
        return vm
    }

    private fun AppLockViewModel.backgroundFor(millis: Long) {
        onAction(AppLockUiAction.AppBackgrounded)
        clock.now += millis
        onAction(AppLockUiAction.AppForegrounded)
    }

    @Test
    fun withoutTheGuardALongTripRelocks() = runTest {
        val vm = unlockedViewModel()
        vm.backgroundFor(5 * minute)
        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun withTheGuardOpenALongTripDoesNotRelock() = runTest {
        val vm = unlockedViewModel()
        guard.begin()
        vm.backgroundFor(5 * minute)
        assertFalse(vm.uiState.value.isLocked)
    }

    @Test
    fun onceTheFlowEndsTheNextTripCountsAgain() = runTest {
        val vm = unlockedViewModel()
        guard.begin()
        vm.backgroundFor(5 * minute)
        guard.end()
        vm.backgroundFor(5 * minute)
        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun aGuardThatWasNeverClosedExpires() = runTest {
        val vm = unlockedViewModel()
        guard.begin()
        vm.backgroundFor(ExternalFlowGuard.MAX_MILLIS + minute)
        assertTrue(vm.uiState.value.isLocked)
    }

    @Test
    fun theGuardReportsItsStateAndExpiry() {
        assertFalse(guard.isActive())
        guard.begin()
        assertTrue(guard.isActive())
        clock.now = ExternalFlowGuard.MAX_MILLIS - 1
        assertTrue(guard.isActive())
        clock.now = ExternalFlowGuard.MAX_MILLIS + 1
        assertFalse(guard.isActive())
        guard.begin()
        guard.end()
        assertFalse(guard.isActive())
    }
}
