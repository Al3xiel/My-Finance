package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DivergenceReport
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.WorkspaceSummary

/** `LOCKED` = todavía no se confirmó la credencial: no se muestra nada del espacio (S64 re-autentica al entrar). */
enum class WorkspaceStage {
    LOCKED, LOADING, EMPTY, READY, PICKING_PACKAGE, PICKER_OPEN_PACKAGE, PICKING_DICTIONARY, PICKER_OPEN_DICTIONARY, WORKING,
}

enum class WorkspaceMessage {
    AUTH_FAILED, ALREADY_OPEN, NOT_A_PACKAGE, UNSUPPORTED_PACKAGE, WRONG_PASSWORD, READ_FAILED,
    DICTIONARY_MISMATCH, DICTIONARY_INVALID, UNEXPECTED,
}

enum class PasswordTarget { PACKAGE, DICTIONARY }

data class DiagnosticWorkspaceUiState(
    val stage: WorkspaceStage = WorkspaceStage.LOCKED,
    val summary: WorkspaceSummary? = null,
    val report: DivergenceReport? = null,
    /** seudónimo → alias real, solo si hay un diccionario aplicado; vive únicamente en el espacio. */
    val names: Map<String, String> = emptyMap(),
    val requestSelection: Set<String> = emptySet(),
    val requestCode: String? = null,
    val askPassword: PasswordTarget? = null,
    val askDelete: Boolean = false,
    val message: WorkspaceMessage? = null,
)

sealed interface DiagnosticWorkspaceUiAction {
    data class Authenticate(val prompt: AuthPrompt) : DiagnosticWorkspaceUiAction
    data object OpenPackage : DiagnosticWorkspaceUiAction
    data object PickerOpened : DiagnosticWorkspaceUiAction
    data class PackagePicked(val uri: String?) : DiagnosticWorkspaceUiAction
    data object ApplyDictionary : DiagnosticWorkspaceUiAction
    data class DictionaryPicked(val uri: String?) : DiagnosticWorkspaceUiAction

    /** La contraseña (del paquete o del diccionario) la borra el ViewModel. */
    data class SubmitPassword(val password: CharArray) : DiagnosticWorkspaceUiAction
    data object CancelPassword : DiagnosticWorkspaceUiAction
    data class ToggleRequestPseudonym(val pseudonym: String) : DiagnosticWorkspaceUiAction
    data object CreateRequest : DiagnosticWorkspaceUiAction
    data object DismissRequest : DiagnosticWorkspaceUiAction
    data object AskDelete : DiagnosticWorkspaceUiAction
    data object ConfirmDelete : DiagnosticWorkspaceUiAction
    data object DismissDelete : DiagnosticWorkspaceUiAction
    data object DismissMessage : DiagnosticWorkspaceUiAction
}
