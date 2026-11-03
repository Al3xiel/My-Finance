package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.core.security.ExternalFlowGuard
import com.al3xiel.my_finances.core.security.container.BackupPassword
import com.al3xiel.my_finances.core.security.container.BackupPasswordError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.SelectDiagnosticEventsUseCase
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S63: exportar un paquete de diagnóstico. Con el bloqueo activo pide la credencial antes de abrir el selector (D48); la
 * contraseña, si la hay, vive en memoria solo hasta que termina la escritura y se borra siempre.
 */
@HiltViewModel(assistedFactory = DiagnosticExportViewModel.Factory::class)
class DiagnosticExportViewModel @AssistedInject constructor(
    @Assisted reconciliationIdValue: String?,
    private val select: SelectDiagnosticEventsUseCase,
    private val export: ExportDiagnosticUseCase,
    private val securitySettings: SecuritySettingsRepository,
    private val authenticator: Authenticator,
    private val externalFlow: ExternalFlowGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(reconciliationIdValue: String?): DiagnosticExportViewModel
    }

    private val reconciliationId = reconciliationIdValue?.let { ReconciliationId(it) }

    private val scopes = if (reconciliationId != null) {
        listOf(DiagnosticScope.RECONCILIATION, DiagnosticScope.ACCOUNT_HISTORY, DiagnosticScope.FULL)
    } else {
        listOf(DiagnosticScope.FULL)
    }

    private val state = MutableStateFlow(DiagnosticExportUiState(scope = scopes.first(), availableScopes = scopes))
    val uiState: StateFlow<DiagnosticExportUiState> = state.asStateFlow()

    private var password: CharArray? = null
    private var authenticating = false

    init {
        refreshPreview()
    }

    fun onAction(action: DiagnosticExportUiAction) {
        when (action) {
            is DiagnosticExportUiAction.SetScope -> if (action.scope in scopes && state.value.stage == ExportStage.FORM) {
                state.update { it.copy(scope = action.scope) }
                refreshPreview()
            }
            is DiagnosticExportUiAction.SetAnonymize -> state.update { it.copy(anonymize = action.on) }
            is DiagnosticExportUiAction.SetUsePassword -> state.update { it.copy(usePassword = action.on) }
            is DiagnosticExportUiAction.Submit -> submit(action)
            DiagnosticExportUiAction.PickerOpened -> pickerOpened()
            is DiagnosticExportUiAction.PickerResult -> picked(action.uri)
            DiagnosticExportUiAction.Cancel -> cancel()
            DiagnosticExportUiAction.DismissMessage -> state.update { it.copy(message = null) }
        }
    }

    private fun request() = DiagnosticSelectionRequest(state.value.scope, reconciliationId)

    private fun refreshPreview() {
        viewModelScope.launch {
            val chosen = withContext(ioDispatcher) { select(request()) }
            state.update {
                when (chosen) {
                    is Outcome.Ok -> it.copy(
                        preview = ExportPreview(
                            chosen.value.eventCount, chosen.value.firstOccurredOn, chosen.value.lastOccurredOn, chosen.value.anonymizedFields,
                        ),
                        message = null,
                    )
                    is Outcome.Failed -> it.copy(preview = null, message = ExportMessage.NOT_FOUND)
                }
            }
        }
    }

    private fun submit(action: DiagnosticExportUiAction.Submit) {
        val current = state.value
        if (authenticating || current.stage != ExportStage.FORM) return BackupPassword.wipe(action.password, action.confirmation)
        val problem = if (current.usePassword) {
            when (BackupPassword.check(action.password, action.confirmation)) {
                BackupPasswordError.TOO_SHORT -> ExportMessage.PASSWORD_TOO_SHORT
                BackupPasswordError.TOO_LONG -> ExportMessage.PASSWORD_TOO_LONG
                BackupPasswordError.MISMATCH -> ExportMessage.PASSWORD_MISMATCH
                null -> null
            }
        } else {
            null
        }
        BackupPassword.wipe(action.confirmation)
        if (problem != null) {
            BackupPassword.wipe(action.password)
            state.update { it.copy(message = problem) }
            return
        }
        if (!current.usePassword) BackupPassword.wipe(action.password)
        authenticating = true
        viewModelScope.launch {
            val authenticated = try {
                reauthenticate(action.prompt)
            } finally {
                authenticating = false
            }
            if (!authenticated) {
                BackupPassword.wipe(action.password)
                return@launch
            }
            password = if (current.usePassword) action.password else null
            state.update { it.copy(stage = ExportStage.PICKING, message = null) }
        }
    }

    /** Con el bloqueo activo, sacar datos del teléfono pide la credencial otra vez (D48). */
    private suspend fun reauthenticate(prompt: AuthPrompt): Boolean {
        val lockOn = securitySettings.settings.first().lockEnabled
        if (!lockOn || authenticator.availability() != AuthAvailability.READY) return true
        return when (authenticator.authenticate(prompt)) {
            AuthResult.Success -> true
            AuthResult.Cancelled -> false
            AuthResult.LockedOut, AuthResult.Failed -> {
                state.update { it.copy(message = ExportMessage.AUTH_FAILED) }
                false
            }
        }
    }

    private fun pickerOpened() {
        if (state.value.stage != ExportStage.PICKING) return
        externalFlow.begin()
        state.update { it.copy(stage = ExportStage.PICKER_OPEN) }
    }

    private fun picked(uri: String?) {
        externalFlow.end()
        val stage = state.value.stage
        if (uri == null || (stage != ExportStage.PICKING && stage != ExportStage.PICKER_OPEN)) {
            reset()
            return
        }
        val secret = password
        val current = state.value
        state.update { it.copy(stage = ExportStage.WRITING) }
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) { export(request(), uri, secret, current.anonymize) }
            } finally {
                wipePassword()
            }
            state.update {
                when (result) {
                    is ExportDiagnosticResult.Created -> it.copy(stage = ExportStage.DONE, created = result)
                    ExportDiagnosticResult.Cancelled -> it.copy(stage = ExportStage.FORM)
                    ExportDiagnosticResult.NotFound -> it.copy(stage = ExportStage.FORM, message = ExportMessage.NOT_FOUND)
                    is ExportDiagnosticResult.WriteFailed -> it.copy(stage = ExportStage.FORM, message = ExportMessage.CREATE_FAILED)
                    is ExportDiagnosticResult.VerificationFailed -> it.copy(stage = ExportStage.FORM, message = ExportMessage.VERIFY_FAILED)
                }
            }
        }
    }

    private fun cancel() {
        externalFlow.end()
        reset()
    }

    private fun reset() {
        wipePassword()
        state.update { it.copy(stage = ExportStage.FORM) }
    }

    private fun wipePassword() {
        password?.let { BackupPassword.wipe(it) }
        password = null
    }

    override fun onCleared() {
        externalFlow.end()
        wipePassword()
    }
}
