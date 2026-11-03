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
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.DictionaryPreviewResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDictionaryResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDictionaryUseCase
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
 * S65: exportar un diccionario `.fkey` desde la conciliación que produjo el paquete (DG7). Muestra la lista exacta de lo que se
 * revelará antes de pedir la contraseña (obligatoria) y la credencial; la contraseña se borra siempre al terminar.
 */
@HiltViewModel(assistedFactory = DictionaryExportViewModel.Factory::class)
class DictionaryExportViewModel @AssistedInject constructor(
    @Assisted reconciliationIdValue: String,
    private val export: ExportDictionaryUseCase,
    private val securitySettings: SecuritySettingsRepository,
    private val authenticator: Authenticator,
    private val externalFlow: ExternalFlowGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(reconciliationIdValue: String): DictionaryExportViewModel
    }

    private val reconciliationId = ReconciliationId(reconciliationIdValue)
    private val state = MutableStateFlow(DictionaryExportUiState())
    val uiState: StateFlow<DictionaryExportUiState> = state.asStateFlow()

    private var password: CharArray? = null
    private var authenticating = false

    fun onAction(action: DictionaryExportUiAction) {
        when (action) {
            is DictionaryExportUiAction.SetRequestCode -> {
                state.update { it.copy(requestCode = action.code) }
                refreshPreview()
            }
            is DictionaryExportUiAction.SetScope -> {
                state.update { it.copy(scope = action.scope) }
                refreshPreview()
            }
            is DictionaryExportUiAction.Submit -> submit(action)
            DictionaryExportUiAction.PickerOpened -> pickerOpened()
            is DictionaryExportUiAction.PickerResult -> picked(action.uri)
            DictionaryExportUiAction.Cancel -> {
                externalFlow.end()
                reset()
            }
            DictionaryExportUiAction.DismissMessage -> state.update { it.copy(message = null) }
        }
    }

    private fun refreshPreview() {
        val current = state.value
        if (current.stage != DictionaryStage.FORM) return
        if (current.requestCode.isBlank()) {
            state.update { it.copy(preview = null, message = null) }
            return
        }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { export.preview(current.requestCode, current.scope, reconciliationId) }
            state.update {
                when (result) {
                    is DictionaryPreviewResult.Ready -> it.copy(preview = DictionaryPreview(result.pseudonyms, result.noteEventIds, result.unknown), message = null)
                    DictionaryPreviewResult.InvalidRequest -> it.copy(preview = null, message = DictionaryMessage.INVALID_REQUEST)
                    DictionaryPreviewResult.PackageMismatch -> it.copy(preview = null, message = DictionaryMessage.PACKAGE_MISMATCH)
                    DictionaryPreviewResult.NotFromReconciliation -> it.copy(preview = null, message = DictionaryMessage.NOT_FROM_RECONCILIATION)
                }
            }
        }
    }

    private fun submit(action: DictionaryExportUiAction.Submit) {
        val current = state.value
        if (authenticating || current.stage != DictionaryStage.FORM || current.preview == null) {
            return BackupPassword.wipe(action.password, action.confirmation)
        }
        val problem = when (BackupPassword.check(action.password, action.confirmation)) {
            BackupPasswordError.TOO_SHORT -> DictionaryMessage.PASSWORD_TOO_SHORT
            BackupPasswordError.TOO_LONG -> DictionaryMessage.PASSWORD_TOO_LONG
            BackupPasswordError.MISMATCH -> DictionaryMessage.PASSWORD_MISMATCH
            null -> null
        }
        BackupPassword.wipe(action.confirmation)
        if (problem != null) {
            BackupPassword.wipe(action.password)
            state.update { it.copy(message = problem) }
            return
        }
        authenticating = true
        viewModelScope.launch {
            var handedOver = false
            try {
                val authenticated = try {
                    reauthenticate(action.prompt)
                } finally {
                    authenticating = false
                }
                if (!authenticated) return@launch
                handedOver = true
            } finally {
                // La contraseña solo sigue viva si pasó a `password`; en cualquier otra salida (también al cancelarse) se borra.
                if (!handedOver) BackupPassword.wipe(action.password)
            }
            password = action.password
            state.update { it.copy(stage = DictionaryStage.PICKING, message = null) }
        }
    }

    /** Revelar nombres reales pide la credencial otra vez cuando el bloqueo está activo (D48). */
    private suspend fun reauthenticate(prompt: AuthPrompt): Boolean {
        val lockOn = securitySettings.settings.first().lockEnabled
        if (!lockOn || authenticator.availability() != AuthAvailability.READY) return true
        return when (authenticator.authenticate(prompt)) {
            AuthResult.Success -> true
            AuthResult.Cancelled -> false
            AuthResult.LockedOut, AuthResult.Failed -> {
                state.update { it.copy(message = DictionaryMessage.AUTH_FAILED) }
                false
            }
        }
    }

    private fun pickerOpened() {
        if (state.value.stage != DictionaryStage.PICKING) return
        externalFlow.begin()
        state.update { it.copy(stage = DictionaryStage.PICKER_OPEN) }
    }

    private fun picked(uri: String?) {
        externalFlow.end()
        val stage = state.value.stage
        val secret = password
        if (uri == null || secret == null || (stage != DictionaryStage.PICKING && stage != DictionaryStage.PICKER_OPEN)) {
            reset()
            return
        }
        val current = state.value
        state.update { it.copy(stage = DictionaryStage.WRITING) }
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) { export(current.requestCode, current.scope, uri, secret, reconciliationId) }
            } finally {
                wipePassword()
            }
            state.update {
                when (result) {
                    is ExportDictionaryResult.Created -> it.copy(stage = DictionaryStage.DONE, created = result)
                    ExportDictionaryResult.Cancelled -> it.copy(stage = DictionaryStage.FORM)
                    ExportDictionaryResult.InvalidRequest -> it.copy(stage = DictionaryStage.FORM, message = DictionaryMessage.INVALID_REQUEST)
                    ExportDictionaryResult.PackageMismatch -> it.copy(stage = DictionaryStage.FORM, message = DictionaryMessage.PACKAGE_MISMATCH)
                    ExportDictionaryResult.NotFromReconciliation -> it.copy(stage = DictionaryStage.FORM, message = DictionaryMessage.NOT_FROM_RECONCILIATION)
                    ExportDictionaryResult.PasswordInvalid -> it.copy(stage = DictionaryStage.FORM, message = DictionaryMessage.PASSWORD_TOO_SHORT)
                    is ExportDictionaryResult.WriteFailed -> it.copy(stage = DictionaryStage.FORM, message = DictionaryMessage.CREATE_FAILED)
                    is ExportDictionaryResult.VerificationFailed -> it.copy(stage = DictionaryStage.FORM, message = DictionaryMessage.VERIFY_FAILED)
                }
            }
        }
    }

    private fun reset() {
        wipePassword()
        state.update { it.copy(stage = DictionaryStage.FORM) }
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
