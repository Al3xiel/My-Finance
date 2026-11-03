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
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageException.Reason
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ApplyDictionaryResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ApplyDictionaryUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.BuildDivergenceReportUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.CreateDictionaryRequestUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.CreateRequestResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.DeleteDiagnosticWorkspaceUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.LoadWorkspaceUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.OpenDiagnosticPackageUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.OpenPackageResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ReportOutcome
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * S64: el espacio de diagnóstico. No muestra nada hasta confirmar la credencial (cuando el bloqueo está activo). Un solo paquete
 * a la vez: abrir otro pide borrar el actual. Las contraseñas se borran siempre; el diccionario solo existe dentro del espacio.
 */
@HiltViewModel
class DiagnosticWorkspaceViewModel @Inject constructor(
    private val load: LoadWorkspaceUseCase,
    private val open: OpenDiagnosticPackageUseCase,
    private val buildReport: BuildDivergenceReportUseCase,
    private val createRequest: CreateDictionaryRequestUseCase,
    private val apply: ApplyDictionaryUseCase,
    private val delete: DeleteDiagnosticWorkspaceUseCase,
    private val securitySettings: SecuritySettingsRepository,
    private val authenticator: Authenticator,
    private val externalFlow: ExternalFlowGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow(DiagnosticWorkspaceUiState())
    val uiState: StateFlow<DiagnosticWorkspaceUiState> = state.asStateFlow()

    private var pendingUri: String? = null
    private var authenticating = false

    fun onAction(action: DiagnosticWorkspaceUiAction) {
        if (action is DiagnosticWorkspaceUiAction.Authenticate) return authenticate(action.prompt)
        if (state.value.stage == WorkspaceStage.LOCKED) {
            if (action is DiagnosticWorkspaceUiAction.SubmitPassword) BackupPassword.wipe(action.password)
            return
        }
        when (action) {
            is DiagnosticWorkspaceUiAction.Authenticate -> Unit
            DiagnosticWorkspaceUiAction.OpenPackage -> startPicking(WorkspaceStage.PICKING_PACKAGE)
            DiagnosticWorkspaceUiAction.ApplyDictionary -> if (state.value.summary != null) startPicking(WorkspaceStage.PICKING_DICTIONARY)
            DiagnosticWorkspaceUiAction.PickerOpened -> pickerOpened()
            is DiagnosticWorkspaceUiAction.PackagePicked -> packagePicked(action.uri)
            is DiagnosticWorkspaceUiAction.DictionaryPicked -> dictionaryPicked(action.uri)
            is DiagnosticWorkspaceUiAction.SubmitPassword -> submitPassword(action.password)
            DiagnosticWorkspaceUiAction.CancelPassword -> {
                pendingUri = null
                state.update { it.copy(askPassword = null, stage = settledStage(it)) }
            }
            is DiagnosticWorkspaceUiAction.ToggleRequestPseudonym -> state.update {
                val chosen = if (action.pseudonym in it.requestSelection) it.requestSelection - action.pseudonym else it.requestSelection + action.pseudonym
                it.copy(requestSelection = chosen)
            }
            DiagnosticWorkspaceUiAction.CreateRequest -> createTheRequest()
            DiagnosticWorkspaceUiAction.DismissRequest -> state.update { it.copy(requestCode = null) }
            DiagnosticWorkspaceUiAction.AskDelete -> state.update { it.copy(askDelete = true) }
            DiagnosticWorkspaceUiAction.DismissDelete -> state.update { it.copy(askDelete = false) }
            DiagnosticWorkspaceUiAction.ConfirmDelete -> deleteWorkspace()
            DiagnosticWorkspaceUiAction.DismissMessage -> state.update { it.copy(message = null) }
        }
    }

    private fun settledStage(s: DiagnosticWorkspaceUiState) = if (s.summary != null) WorkspaceStage.READY else WorkspaceStage.EMPTY

    private fun authenticate(prompt: AuthPrompt) {
        if (authenticating || state.value.stage != WorkspaceStage.LOCKED) return
        authenticating = true
        viewModelScope.launch {
            val ok = try {
                reauthenticate(prompt)
            } finally {
                authenticating = false
            }
            if (ok) refresh(WorkspaceStage.LOADING)
        }
    }

    private suspend fun reauthenticate(prompt: AuthPrompt): Boolean {
        val lockOn = securitySettings.settings.first().lockEnabled
        if (!lockOn || authenticator.availability() != AuthAvailability.READY) return true
        return when (authenticator.authenticate(prompt)) {
            AuthResult.Success -> true
            AuthResult.Cancelled -> false
            AuthResult.LockedOut, AuthResult.Failed -> {
                state.update { it.copy(message = WorkspaceMessage.AUTH_FAILED) }
                false
            }
        }
    }

    /** Vuelve a leer el espacio: resumen, reporte de divergencias y diccionario aplicado. */
    private suspend fun refresh(intermediate: WorkspaceStage) {
        state.update { it.copy(stage = intermediate, message = null) }
        val summary = withContext(ioDispatcher) { load() }
        if (summary == null) {
            state.update { DiagnosticWorkspaceUiState(stage = WorkspaceStage.EMPTY) }
            return
        }
        val outcome = withContext(ioDispatcher) { buildReport() }
        val dictionary = withContext(ioDispatcher) { apply.applied() }
        state.update {
            it.copy(
                stage = WorkspaceStage.READY,
                summary = summary,
                report = (outcome as? ReportOutcome.Ready)?.report,
                names = dictionary?.aliases.orEmpty(),
                askPassword = null,
            )
        }
    }

    private fun startPicking(stage: WorkspaceStage) {
        val current = state.value.stage
        if (current != WorkspaceStage.EMPTY && current != WorkspaceStage.READY) return
        state.update { it.copy(stage = stage, message = null) }
    }

    private fun pickerOpened() {
        val next = when (state.value.stage) {
            WorkspaceStage.PICKING_PACKAGE -> WorkspaceStage.PICKER_OPEN_PACKAGE
            WorkspaceStage.PICKING_DICTIONARY -> WorkspaceStage.PICKER_OPEN_DICTIONARY
            else -> return
        }
        externalFlow.begin()
        state.update { it.copy(stage = next) }
    }

    private fun packagePicked(uri: String?) {
        externalFlow.end()
        if (uri == null || state.value.stage !in PACKAGE_PICKING) {
            state.update { it.copy(stage = settledStage(it)) }
            return
        }
        pendingUri = uri
        openPackage(null)
    }

    private fun openPackage(secret: CharArray?) {
        val uri = pendingUri ?: return
        state.update { it.copy(stage = WorkspaceStage.WORKING, message = null) }
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) { open(uri, secret) }
            } finally {
                secret?.let { BackupPassword.wipe(it) }
            }
            when (result) {
                is OpenPackageResult.Opened -> {
                    pendingUri = null
                    refresh(WorkspaceStage.WORKING)
                }
                is OpenPackageResult.Rejected -> if (result.reason == Reason.PASSWORD_REQUIRED || result.reason == Reason.WRONG_PASSWORD) {
                    val wrong = result.reason == Reason.WRONG_PASSWORD
                    state.update {
                        it.copy(stage = settledStage(it), askPassword = PasswordTarget.PACKAGE, message = if (wrong) WorkspaceMessage.WRONG_PASSWORD else null)
                    }
                } else {
                    pendingUri = null
                    state.update { it.copy(stage = settledStage(it), askPassword = null, message = result.message()) }
                }
                else -> {
                    pendingUri = null
                    state.update { it.copy(stage = settledStage(it), askPassword = null, message = result.message()) }
                }
            }
        }
    }

    private fun OpenPackageResult.message() = when (this) {
        OpenPackageResult.AlreadyOpen -> WorkspaceMessage.ALREADY_OPEN
        is OpenPackageResult.Rejected -> when (reason) {
            Reason.NEWER_VERSION, Reason.UNKNOWN_EVENT -> WorkspaceMessage.UNSUPPORTED_PACKAGE
            Reason.WRONG_PASSWORD -> WorkspaceMessage.WRONG_PASSWORD
            else -> WorkspaceMessage.NOT_A_PACKAGE
        }
        OpenPackageResult.ReadFailed -> WorkspaceMessage.READ_FAILED
        else -> WorkspaceMessage.UNEXPECTED
    }

    private fun dictionaryPicked(uri: String?) {
        externalFlow.end()
        if (uri == null || state.value.stage !in DICTIONARY_PICKING) {
            state.update { it.copy(stage = settledStage(it)) }
            return
        }
        pendingUri = uri
        state.update { it.copy(stage = settledStage(it), askPassword = PasswordTarget.DICTIONARY) }
    }

    private fun submitPassword(secret: CharArray) {
        val target = state.value.askPassword
        if (target == null || pendingUri == null) return BackupPassword.wipe(secret)
        state.update { it.copy(askPassword = null) }
        if (target == PasswordTarget.PACKAGE) return openPackage(secret)
        val uri = pendingUri
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) { apply(uri, secret) }
            } finally {
                BackupPassword.wipe(secret)
            }
            when (result) {
                is ApplyDictionaryResult.Applied -> {
                    pendingUri = null
                    refresh(WorkspaceStage.WORKING)
                }
                ApplyDictionaryResult.WrongPassword ->
                    state.update { it.copy(askPassword = PasswordTarget.DICTIONARY, message = WorkspaceMessage.WRONG_PASSWORD) }
                else -> {
                    pendingUri = null
                    state.update { it.copy(message = result.message()) }
                }
            }
        }
    }

    private fun ApplyDictionaryResult.message() = when (this) {
        ApplyDictionaryResult.Mismatch -> WorkspaceMessage.DICTIONARY_MISMATCH
        ApplyDictionaryResult.NotADictionary -> WorkspaceMessage.DICTIONARY_INVALID
        ApplyDictionaryResult.ReadFailed -> WorkspaceMessage.READ_FAILED
        else -> WorkspaceMessage.UNEXPECTED
    }

    private fun createTheRequest() {
        val chosen = state.value.requestSelection.toList()
        if (chosen.isEmpty()) return
        val result = createRequest(chosen, emptyList())
        if (result is CreateRequestResult.Ready) state.update { it.copy(requestCode = result.code) }
    }

    private fun deleteWorkspace() {
        viewModelScope.launch {
            withContext(ioDispatcher) { delete() }
            pendingUri = null
            state.update { DiagnosticWorkspaceUiState(stage = WorkspaceStage.EMPTY) }
        }
    }

    override fun onCleared() {
        externalFlow.end()
    }

    private companion object {
        val PACKAGE_PICKING = setOf(WorkspaceStage.PICKING_PACKAGE, WorkspaceStage.PICKER_OPEN_PACKAGE)
        val DICTIONARY_PICKING = setOf(WorkspaceStage.PICKING_DICTIONARY, WorkspaceStage.PICKER_OPEN_DICTIONARY)
    }
}
