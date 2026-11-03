package com.al3xiel.my_finances.feature.privacy.ui

import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.feature.privacy.domain.repository.DevToolsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeDevToolsRepository(initial: Boolean = false) : DevToolsRepository {
    val state = MutableStateFlow(initial)
    override val enabled = state
    override suspend fun setEnabled(enabled: Boolean) {
        state.value = enabled
    }
}

private class SteppingClock(var now: Long = 0L) : MonotonicClock {
    override fun nowMillis() = now
}

@OptIn(ExperimentalCoroutinesApi::class)
class AboutSectionTapTest {
    private val repository = FakeDevToolsRepository()
    private val clock = SteppingClock()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(enabled: Boolean = false) = AboutViewModel(FakeDevToolsRepository(enabled).also { repo -> repoRef = repo }, clock, "1.2.3")
    private var repoRef: FakeDevToolsRepository = repository

    private fun AboutViewModel.tap(times: Int, gapMillis: Long = 300) = repeat(times) {
        clock.now += gapMillis
        onAction(AboutUiAction.VersionTapped)
    }

    @Test
    fun showsTheVersionAndStartsWithToolsOff() {
        val state = viewModel().uiState.value
        assertEquals("1.2.3", state.version)
        assertFalse(state.devToolsEnabled)
        assertNull(state.tapsRemaining)
    }

    @Test
    fun sevenQuickTapsEnableTheTools() {
        val vm = viewModel()
        vm.tap(6)
        assertFalse(repoRef.state.value)
        vm.tap(1)
        assertTrue(repoRef.state.value)
        assertTrue(vm.uiState.value.devToolsEnabled)
    }

    @Test
    fun theLastTapsSayHowManyAreLeft() {
        val vm = viewModel()
        vm.tap(1)
        assertNull(vm.uiState.value.tapsRemaining)
        vm.tap(4)
        assertEquals(2, vm.uiState.value.tapsRemaining)
    }

    @Test
    fun aPauseRestartsTheCount() {
        val vm = viewModel()
        vm.tap(5)
        clock.now += 5_000
        vm.tap(2)
        assertFalse(repoRef.state.value)
        vm.tap(5)
        assertTrue(repoRef.state.value)
    }

    @Test
    fun tappingAfterTheyAreOnChangesNothing() {
        val vm = viewModel(enabled = true)
        vm.tap(10)
        assertTrue(vm.uiState.value.devToolsEnabled)
        assertNull(vm.uiState.value.tapsRemaining)
    }
}
