package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.TagId
import java.time.LocalDate

sealed interface ExpenseEditorUiAction {
    data class EditTotal(val text: String) : ExpenseEditorUiAction
    data class SelectAccount(val id: AccountId) : ExpenseEditorUiAction
    data class SelectOccurredOn(val occurredOn: LocalDate) : ExpenseEditorUiAction
    data class EditNote(val text: String) : ExpenseEditorUiAction
    data class EditPartAmount(val key: Int, val text: String) : ExpenseEditorUiAction
    data class SelectPartCategory(val key: Int, val id: CategoryId) : ExpenseEditorUiAction
    data class TogglePartTag(val key: Int, val id: TagId) : ExpenseEditorUiAction
    data object AddPart : ExpenseEditorUiAction
    data class RemovePart(val key: Int) : ExpenseEditorUiAction
    data object Save : ExpenseEditorUiAction
    data class ConfirmReason(val reason: String) : ExpenseEditorUiAction
    data object DismissReason : ExpenseEditorUiAction
    data object ConfirmNegativeBalance : ExpenseEditorUiAction
    data object DismissWarning : ExpenseEditorUiAction
}
