package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.onboarding.domain.repository.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupSetupUiState(
    val reminderEnabled: Boolean = false,
    val intervalDays: Int = BackupSettingsRepository.DEFAULT_INTERVAL_DAYS,
    val showExplanation: Boolean = false,
    val requestPermission: Boolean = false,
    val permissionDenied: Boolean = false,
    /** La bandera `OnboardingCompleted` quedó puesta: la compuerta entra a la app. */
    val finished: Boolean = false,
)

sealed interface BackupSetupUiAction {
    data class ToggleReminder(val on: Boolean, val permissionGranted: Boolean) : BackupSetupUiAction
    data object AcceptExplanation : BackupSetupUiAction
    data object DismissExplanation : BackupSetupUiAction
    data object PermissionRequestLaunched : BackupSetupUiAction
    data class PermissionResult(val granted: Boolean) : BackupSetupUiAction
    data class SetIntervalDays(val days: Int) : BackupSetupUiAction
    data object Finish : BackupSetupUiAction
}

/** S05: el recordatorio es opcional y, si se niega `POST_NOTIFICATIONS`, todo lo demás sigue funcionando. */
@HiltViewModel
class BackupSetupViewModel @Inject constructor(
    private val settings: BackupSettingsRepository,
    private val scheduler: BackupReminderScheduler,
    private val onboarding: OnboardingRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BackupSetupUiState())
    val uiState: StateFlow<BackupSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.enabled.collect { on -> _uiState.update { it.copy(reminderEnabled = on) } }
        }
        viewModelScope.launch {
            settings.intervalDays.collect { days -> _uiState.update { it.copy(intervalDays = days) } }
        }
    }

    fun onAction(action: BackupSetupUiAction) {
        when (action) {
            is BackupSetupUiAction.ToggleReminder -> when {
                !action.on -> setReminder(false)
                action.permissionGranted -> setReminder(true)
                else -> _uiState.update { it.copy(showExplanation = true, permissionDenied = false) }
            }
            BackupSetupUiAction.AcceptExplanation ->
                _uiState.update { it.copy(showExplanation = false, requestPermission = true) }
            BackupSetupUiAction.DismissExplanation -> _uiState.update { it.copy(showExplanation = false) }
            BackupSetupUiAction.PermissionRequestLaunched -> _uiState.update { it.copy(requestPermission = false) }
            is BackupSetupUiAction.PermissionResult ->
                if (action.granted) setReminder(true) else _uiState.update { it.copy(permissionDenied = true) }
            is BackupSetupUiAction.SetIntervalDays -> viewModelScope.launch { settings.setIntervalDays(action.days) }
            BackupSetupUiAction.Finish -> viewModelScope.launch {
                onboarding.markCompleted()
                _uiState.update { it.copy(finished = true) }
            }
        }
    }

    private fun setReminder(on: Boolean) {
        _uiState.update { it.copy(permissionDenied = false) }
        viewModelScope.launch {
            settings.setEnabled(on)
            if (on) scheduler.ensureScheduled() else scheduler.cancel()
        }
    }
}
