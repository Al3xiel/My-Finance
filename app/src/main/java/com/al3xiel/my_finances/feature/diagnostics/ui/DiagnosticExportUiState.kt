package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticResult
import java.time.LocalDate

/** Pasos de S63. El selector del sistema (`CreateDocument`) se abre en `PICKING` y devuelve su resultado en `PICKER_OPEN`. */
enum class ExportStage { FORM, PICKING, PICKER_OPEN, WRITING, DONE }

enum class ExportMessage { NOT_FOUND, PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_MISMATCH, AUTH_FAILED, CREATE_FAILED, VERIFY_FAILED, UNEXPECTED }

/** Lo que verá quien exporta antes de confirmar: cuántos eventos, de qué fechas y qué campos se anonimizarían. */
data class ExportPreview(
    val eventCount: Int,
    val firstOccurredOn: LocalDate?,
    val lastOccurredOn: LocalDate?,
    val anonymizedFields: List<String>,
)

data class DiagnosticExportUiState(
    val stage: ExportStage = ExportStage.FORM,
    val scope: DiagnosticScope = DiagnosticScope.FULL,
    val availableScopes: List<DiagnosticScope> = listOf(DiagnosticScope.FULL),
    val anonymize: Boolean = true,
    val usePassword: Boolean = false,
    val preview: ExportPreview? = null,
    val message: ExportMessage? = null,
    val created: ExportDiagnosticResult.Created? = null,
)

sealed interface DiagnosticExportUiAction {
    data class SetScope(val scope: DiagnosticScope) : DiagnosticExportUiAction
    data class SetAnonymize(val on: Boolean) : DiagnosticExportUiAction
    data class SetUsePassword(val on: Boolean) : DiagnosticExportUiAction

    /** Las dos contraseñas llegan como `CharArray` y el ViewModel las borra; con `usePassword` apagado se ignoran. */
    data class Submit(val password: CharArray, val confirmation: CharArray, val prompt: AuthPrompt) : DiagnosticExportUiAction

    data object PickerOpened : DiagnosticExportUiAction
    data class PickerResult(val uri: String?) : DiagnosticExportUiAction
    data object Cancel : DiagnosticExportUiAction
    data object DismissMessage : DiagnosticExportUiAction
}
