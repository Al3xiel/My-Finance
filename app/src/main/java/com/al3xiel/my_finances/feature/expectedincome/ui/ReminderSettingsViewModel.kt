package com.al3xiel.my_finances.feature.expectedincome.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReminderSettingsUiState(
    val enabled: Boolean = false,
    /** Explica para qué se pide el permiso antes del diálogo del sistema. */
    val showExplanation: Boolean = false,
    /** Pide a la pantalla que lance el diálogo del sistema; ella lo confirma con `PermissionRequestLaunched`. */
    val requestPermission: Boolean = false,
    /** El usuario negó el permiso: el interruptor queda apagado y se le dice cómo activarlo. */
    val permissionDenied: Boolean = false,
)

sealed interface ReminderSettingsUiAction {
    /** `permissionGranted` lo sabe la pantalla (Android 13+ lo pide en ejecución). */
    data class Toggle(val on: Boolean, val permissionGranted: Boolean) : ReminderSettingsUiAction
    data object AcceptExplanation : ReminderSettingsUiAction
    data object DismissExplanation : ReminderSettingsUiAction
    data object PermissionRequestLaunched : ReminderSettingsUiAction
    data class PermissionResult(val granted: Boolean) : ReminderSettingsUiAction
}

/** Sección "Recordatorios" de S72: el interruptor global; al activarlo pide el permiso en contexto y, si se niega, queda apagado. */
@HiltViewModel
class ReminderSettingsViewModel @Inject constructor(
    private val reminders: ReminderRepository,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    private data class Form(
        val showExplanation: Boolean = false,
        val requestPermission: Boolean = false,
        val permissionDenied: Boolean = false,
    )

    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<ReminderSettingsUiState> = combine(reminders.enabled, form) { enabled, form ->
        ReminderSettingsUiState(enabled, form.showExplanation, form.requestPermission, form.permissionDenied)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettingsUiState())

    fun onAction(action: ReminderSettingsUiAction) {
        when (action) {
            is ReminderSettingsUiAction.Toggle -> when {
                !action.on -> setEnabled(false)
                action.permissionGranted -> setEnabled(true)
                else -> form.update { it.copy(showExplanation = true, permissionDenied = false) }
            }
            ReminderSettingsUiAction.AcceptExplanation -> form.update { it.copy(showExplanation = false, requestPermission = true) }
            ReminderSettingsUiAction.DismissExplanation -> form.update { it.copy(showExplanation = false) }
            ReminderSettingsUiAction.PermissionRequestLaunched -> form.update { it.copy(requestPermission = false) }
            is ReminderSettingsUiAction.PermissionResult ->
                if (action.granted) setEnabled(true) else form.update { it.copy(permissionDenied = true) }
        }
    }

    private fun setEnabled(on: Boolean) {
        form.update { it.copy(permissionDenied = false) }
        viewModelScope.launch {
            reminders.setEnabled(on)
            if (on) scheduler.ensureScheduled() else scheduler.cancel()
        }
    }
}
