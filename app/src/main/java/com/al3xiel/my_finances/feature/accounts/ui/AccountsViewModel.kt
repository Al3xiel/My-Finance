package com.al3xiel.my_finances.feature.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.usecase.ArchiveAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RenameAccountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `renamingId == null` = alta; al renombrar solo cambia el nombre. El saldo inicial se teclea como texto. */
data class AccountDialogState(
    val renamingId: AccountId? = null,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val openingBalance: String = "",
    val error: BusinessError? = null,
)

data class AccountsUiState(
    val accounts: List<AccountWithBalance> = emptyList(),
    val dialog: AccountDialogState? = null,
    /** Rechazo de una acción sin diálogo (archivar una cuenta con saldo). */
    val notice: BusinessError? = null,
)

sealed interface AccountsUiAction {
    data object ShowCreate : AccountsUiAction
    data class ShowRename(val id: AccountId, val currentName: String) : AccountsUiAction
    data class EditName(val name: String) : AccountsUiAction
    data class SelectType(val type: AccountType) : AccountsUiAction
    data class EditOpeningBalance(val text: String) : AccountsUiAction
    data object Submit : AccountsUiAction
    data object DismissDialog : AccountsUiAction
    data class Archive(val id: AccountId) : AccountsUiAction
    data object DismissNotice : AccountsUiAction
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accounts: AccountRepository,
    private val createAccount: CreateAccountUseCase,
    private val renameAccount: RenameAccountUseCase,
    private val archiveAccount: ArchiveAccountUseCase,
) : ViewModel() {
    private val dialog = MutableStateFlow<AccountDialogState?>(null)
    private val notice = MutableStateFlow<BusinessError?>(null)

    val uiState: StateFlow<AccountsUiState> = combine(accounts.observeAccounts(), dialog, notice) { list, dialog, notice ->
        AccountsUiState(accounts = list, dialog = dialog, notice = notice)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountsUiState())

    fun onAction(action: AccountsUiAction) {
        when (action) {
            AccountsUiAction.ShowCreate -> dialog.value = AccountDialogState()
            is AccountsUiAction.ShowRename -> dialog.value = AccountDialogState(renamingId = action.id, name = action.currentName)
            is AccountsUiAction.EditName -> dialog.update { it?.copy(name = action.name, error = null) }
            is AccountsUiAction.SelectType -> dialog.update { it?.copy(type = action.type, error = null) }
            is AccountsUiAction.EditOpeningBalance -> dialog.update { it?.copy(openingBalance = action.text, error = null) }
            AccountsUiAction.Submit -> submit()
            AccountsUiAction.DismissDialog -> dialog.value = null
            is AccountsUiAction.Archive -> viewModelScope.launch { notice.value = archiveAccount(action.id).errorOrNull() }
            AccountsUiAction.DismissNotice -> notice.value = null
        }
    }

    private fun submit() {
        val current = dialog.value ?: return
        viewModelScope.launch {
            val outcome = if (current.renamingId != null) {
                renameAccount(current.renamingId, current.name)
            } else {
                // Saldo inicial vacío = 0: una cuenta puede empezar sin dinero (D16).
                val minor = MoneyInputFilter.toMinor(current.openingBalance) ?: 0L
                createAccount(current.name, current.type, null, Money(minor, CurrencyCode.PEN))
            }
            val error = outcome.errorOrNull()
            dialog.update { if (error == null) null else it?.copy(error = error) }
        }
    }
}
