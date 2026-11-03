package com.al3xiel.my_finances.feature.onboarding.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.feature.backup.domain.FakeBackupSettings
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.onboarding.domain.FakeOnboardingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BackupSetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class CountingScheduler : BackupReminderScheduler {
        var ensured = 0
        var cancelled = 0
        override fun ensureScheduled() { ensured++ }
        override fun cancel() { cancelled++ }
    }

    private val settings = FakeBackupSettings(enabled = false)
    private val scheduler = CountingScheduler()
    private val onboarding = FakeOnboardingRepository()

    private fun viewModel() = BackupSetupViewModel(settings, scheduler, onboarding)

    @Test
    fun reminderStartsOff() {
        assertFalse(viewModel().uiState.value.reminderEnabled)
    }

    @Test
    fun enablingWithPermissionSchedulesTheReminder() {
        val vm = viewModel()
        vm.onAction(BackupSetupUiAction.ToggleReminder(on = true, permissionGranted = true))

        assertTrue(vm.uiState.value.reminderEnabled)
        assertEquals(1, scheduler.ensured)
    }

    @Test
    fun enablingWithoutPermissionExplainsThenAsksInContext() {
        val vm = viewModel()
        vm.onAction(BackupSetupUiAction.ToggleReminder(on = true, permissionGranted = false))
        assertTrue(vm.uiState.value.showExplanation)
        assertFalse(vm.uiState.value.reminderEnabled)

        vm.onAction(BackupSetupUiAction.AcceptExplanation)
        assertTrue(vm.uiState.value.requestPermission)
        assertFalse(vm.uiState.value.showExplanation)

        vm.onAction(BackupSetupUiAction.PermissionRequestLaunched)
        vm.onAction(BackupSetupUiAction.PermissionResult(granted = true))
        assertTrue(vm.uiState.value.reminderEnabled)
        assertEquals(1, scheduler.ensured)
    }

    @Test
    fun deniedPermissionLeavesTheReminderOffAndFinishingStillWorks() {
        val vm = viewModel()
        vm.onAction(BackupSetupUiAction.ToggleReminder(on = true, permissionGranted = false))
        vm.onAction(BackupSetupUiAction.AcceptExplanation)
        vm.onAction(BackupSetupUiAction.PermissionResult(granted = false))

        assertTrue(vm.uiState.value.permissionDenied)
        assertFalse(vm.uiState.value.reminderEnabled)
        assertEquals(0, scheduler.ensured)

        vm.onAction(BackupSetupUiAction.Finish)
        assertTrue(onboarding.flag.value)
        assertTrue(vm.uiState.value.finished)
    }

    @Test
    fun turningTheReminderOffCancelsIt() {
        val vm = viewModel()
        vm.onAction(BackupSetupUiAction.ToggleReminder(on = true, permissionGranted = true))
        vm.onAction(BackupSetupUiAction.ToggleReminder(on = false, permissionGranted = true))

        assertFalse(vm.uiState.value.reminderEnabled)
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun intervalIsPersisted() {
        val vm = viewModel()
        vm.onAction(BackupSetupUiAction.SetIntervalDays(30))
        assertEquals(30, vm.uiState.value.intervalDays)
    }

    @Test
    fun finishingMarksTheFlagOnlyOnFinish() {
        val vm = viewModel()
        assertFalse(onboarding.flag.value)

        vm.onAction(BackupSetupUiAction.Finish)

        assertTrue(onboarding.flag.value)
        assertTrue(vm.uiState.value.finished)
    }
}
