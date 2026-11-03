package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDictionaryResult

enum class DictionaryStage { FORM, PICKING, PICKER_OPEN, WRITING, DONE }

enum class DictionaryMessage {
    INVALID_REQUEST, PACKAGE_MISMATCH, NOT_FROM_RECONCILIATION,
    PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_MISMATCH, AUTH_FAILED, CREATE_FAILED, VERIFY_FAILED,
}

/** La lista exacta de lo que se revelará (S65): seudónimos y notas, más cuántos pedidos este teléfono no reconoce. */
data class DictionaryPreview(val pseudonyms: List<String>, val noteEventIds: List<String>, val unknown: Int)

data class DictionaryExportUiState(
    val stage: DictionaryStage = DictionaryStage.FORM,
    val requestCode: String = "",
    val scope: DictionaryScope = DictionaryScope.PARTIAL,
    val preview: DictionaryPreview? = null,
    val message: DictionaryMessage? = null,
    val created: ExportDictionaryResult.Created? = null,
)

sealed interface DictionaryExportUiAction {
    data class SetRequestCode(val code: String) : DictionaryExportUiAction
    data class SetScope(val scope: DictionaryScope) : DictionaryExportUiAction

    /** La contraseña es obligatoria; las dos copias las borra el ViewModel. */
    data class Submit(val password: CharArray, val confirmation: CharArray, val prompt: AuthPrompt) : DictionaryExportUiAction

    data object PickerOpened : DictionaryExportUiAction
    data class PickerResult(val uri: String?) : DictionaryExportUiAction
    data object Cancel : DictionaryExportUiAction
    data object DismissMessage : DictionaryExportUiAction
}
