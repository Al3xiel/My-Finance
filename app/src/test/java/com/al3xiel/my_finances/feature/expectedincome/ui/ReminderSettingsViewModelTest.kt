package com.al3xiel.my_finances.feature.expectedincome.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderLog
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ReminderSettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeReminders : ReminderRepository {
        val state = MutableStateFlow(false)
        override val enabled: Flow<Boolean> get() = state
        override suspend fun isEnabled() = state.value
        override suspend fun setEnabled(enabled: Boolean) { state.value = enabled }
        override suspend fun log(id: ExpectedIncomeId) = ReminderLog(0, null)
        override suspend fun markReminded(ids: Collection<ExpectedIncomeId>, on: LocalDate) = Unit
    }

    private class FakeScheduler : ReminderScheduler {
        val calls = mutableListOf<String>()
        override fun ensureScheduled() { calls += "schedule" }
        override fun cancel() { calls += "cancel" }
    }

    private val reminders = FakeReminders()
    private val scheduler = FakeScheduler()
    private fun viewModel() = ReminderSettingsViewModel(reminders, scheduler)

    @Test
    fun startsOff() {
        assertFalse(viewModel().uiState.value.enabled)
    }

    @Test
    fun withThePermissionAlreadyGrantedTheSwitchTurnsOnAndSchedulesTheWorker() {
        val vm = viewModel()

        vm.onAction(ReminderSettingsUiAction.Toggle(on = true, permissionGranted = true))

        assertTrue(vm.uiState.value.enabled)
        assertEquals(listOf("schedule"), scheduler.calls)
    }

    @Test
    fun withoutThePermissionItExplainsFirstAndOnlyThenAsksTheSystem() {
        val vm = viewModel()

        vm.onAction(ReminderSettingsUiAction.Toggle(on = true, permissionGranted = false))
        assertTrue(vm.uiState.value.showExplanation)
        assertFalse(vm.uiState.value.requestPermission)
        assertFalse(vm.uiState.value.enabled)

        vm.onAction(ReminderSettingsUiAction.AcceptExplanation)
        assertFalse(vm.uiState.value.showExplanation)
        assertTrue(vm.uiState.value.requestPermission)

        vm.onAction(ReminderSettingsUiAction.PermissionRequestLaunched)
        assertFalse(vm.uiState.value.requestPermission)
    }

    @Test
    fun grantingThePermissionTurnsItOn() {
        val vm = viewModel()
        vm.onAction(ReminderSettingsUiAction.Toggle(on = true, permissionGranted = false))
        vm.onAction(ReminderSettingsUiAction.AcceptExplanation)

        vm.onAction(ReminderSettingsUiAction.PermissionResult(granted = true))

        assertTrue(vm.uiState.value.enabled)
        assertEquals(listOf("schedule"), scheduler.calls)
    }

    @Test
    fun denyingThePermissionLeavesItOffAndSaysSo() {
        val vm = viewModel()
        vm.onAction(ReminderSettingsUiAction.Toggle(on = true, permissionGranted = false))
        vm.onAction(ReminderSettingsUiAction.AcceptExplanation)

        vm.onAction(ReminderSettingsUiAction.PermissionResult(granted = false))

        assertFalse(vm.uiState.value.enabled)
        assertTrue(vm.uiState.value.permissionDenied)
        assertTrue(scheduler.calls.isEmpty())
    }

    @Test
    fun declineTheExplanationChangesNothing() {
        val vm = viewModel()
        vm.onAction(ReminderSettingsUiAction.Toggle(on = true, permissionGranted = false))

        vm.onAction(ReminderSettingsUiAction.DismissExplanation)

        assertFalse(vm.uiState.value.showExplanation)
        assertFalse(vm.uiState.value.enabled)
    }

    @Test
    fun turningItOffCancelsTheWorker() {
        val vm = viewModel()
        vm.onAction(ReminderSettingsUiAction.Toggle(on = true, permissionGranted = true))

        vm.onAction(ReminderSettingsUiAction.Toggle(on = false, permissionGranted = true))

        assertFalse(vm.uiState.value.enabled)
        assertEquals(listOf("schedule", "cancel"), scheduler.calls)
    }
}
