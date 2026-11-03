package com.al3xiel.my_finances.feature.quickentry.ui

import com.al3xiel.my_finances.feature.quickentry.domain.model.Ambiguity
import com.al3xiel.my_finances.feature.quickentry.domain.model.Candidate

sealed interface QuickEntryUiAction {
    data class EditLine(val text: String) : QuickEntryUiAction
    data class EditAmount(val text: String) : QuickEntryUiAction
    data class EditNote(val text: String) : QuickEntryUiAction
    data class SelectAccount(val id: String) : QuickEntryUiAction
    data class SelectCategory(val id: String) : QuickEntryUiAction
    data class SelectPool(val id: String) : QuickEntryUiAction
    data class SelectFund(val id: String) : QuickEntryUiAction
    data object ClearPool : QuickEntryUiAction
    data object ClearFund : QuickEntryUiAction
    data class SelectSource(val id: String) : QuickEntryUiAction
    data class Choose(val ambiguity: Ambiguity, val candidate: Candidate) : QuickEntryUiAction
    data object Save : QuickEntryUiAction
    data object ConfirmNegativeBalance : QuickEntryUiAction
    data object DismissWarning : QuickEntryUiAction
}
