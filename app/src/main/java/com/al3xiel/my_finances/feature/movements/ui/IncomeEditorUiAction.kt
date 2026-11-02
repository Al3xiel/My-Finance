package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import java.time.LocalDate

sealed interface IncomeEditorUiAction {
    data class EditAmount(val text: String) : IncomeEditorUiAction
    data class SelectAccount(val id: AccountId) : IncomeEditorUiAction
    data class SelectSource(val id: IncomeSourceId) : IncomeEditorUiAction
    data class SelectOccurredOn(val occurredOn: LocalDate) : IncomeEditorUiAction
    data class EditNote(val text: String) : IncomeEditorUiAction

    /** `null` quita la recarga; elegir un fondo propone apartar todo el ingreso. */
    data class SelectTopUpFund(val id: FundId?) : IncomeEditorUiAction
    data class EditTopUp(val text: String) : IncomeEditorUiAction

    /** Un toque en la sugerencia: este ingreso confirma ese esperado (y propone su monto si aún no hay uno). */
    data class SelectExpected(val id: ExpectedIncomeId) : IncomeEditorUiAction
    data object ClearExpected : IncomeEditorUiAction
    data object Save : IncomeEditorUiAction
    data class ConfirmReason(val reason: String) : IncomeEditorUiAction
    data object DismissReason : IncomeEditorUiAction
    data object ConfirmNegativeBalance : IncomeEditorUiAction
    data object DismissWarning : IncomeEditorUiAction
}
