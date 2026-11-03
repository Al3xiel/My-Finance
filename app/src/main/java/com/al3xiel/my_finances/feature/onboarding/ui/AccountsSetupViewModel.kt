package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateInitialAccountsUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.InitialAccountDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Una fila de la lista de S03; `balance` es el texto tecleado (vacío = 0). */
data class AccountRowUi(val key: Int, val name: String, val type: AccountType, val balance: String = "")

data class AccountsSetupUiState(
    val rows: List<AccountRowUi> = emptyList(),
    val error: BusinessError? = null,
    val saving: Boolean = false,
    /** Las cuentas quedaron creadas: la pantalla avanza a S04. */
    val created: Boolean = false,
)

sealed interface AccountsSetupUiAction {
    data class EditName(val key: Int, val name: String) : AccountsSetupUiAction
    data class EditBalance(val key: Int, val balance: String) : AccountsSetupUiAction
    data class Remove(val key: Int) : AccountsSetupUiAction
    data object Add : AccountsSetupUiAction
    data object Submit : AccountsSetupUiAction
}

@HiltViewModel
class AccountsSetupViewModel @Inject constructor(
    private val createInitialAccounts: CreateInitialAccountsUseCase,
) : ViewModel() {
    private var nextKey = SUGGESTED.size

    private val _uiState = MutableStateFlow(
        AccountsSetupUiState(rows = SUGGESTED.mapIndexed { i, (name, type) -> AccountRowUi(i, name, type) }),
    )
    val uiState: StateFlow<AccountsSetupUiState> = _uiState.asStateFlow()

    fun onAction(action: AccountsSetupUiAction) {
        when (action) {
            is AccountsSetupUiAction.EditName -> editRow(action.key) { it.copy(name = action.name) }
            is AccountsSetupUiAction.EditBalance -> editRow(action.key) { it.copy(balance = action.balance) }
            is AccountsSetupUiAction.Remove -> _uiState.update { state ->
                state.copy(rows = state.rows.filterNot { it.key == action.key }, error = null)
            }
            AccountsSetupUiAction.Add -> _uiState.update {
                it.copy(rows = it.rows + AccountRowUi(nextKey++, "", AccountType.BANK), error = null)
            }
            AccountsSetupUiAction.Submit -> submit()
        }
    }

    private fun editRow(key: Int, change: (AccountRowUi) -> AccountRowUi) {
        _uiState.update { state ->
            state.copy(rows = state.rows.map { if (it.key == key) change(it) else it }, error = null)
        }
    }

    private fun submit() {
        val current = _uiState.value
        if (current.saving || current.created) return
        val drafts = current.rows.map { row ->
            val minor = if (row.balance.isBlank()) 0L else MoneyFormatter.parseMinor(row.balance)
            if (minor == null) return fail(BusinessError.InvalidAmount)
            InitialAccountDraft(row.name, row.type, Money(minor, CurrencyCode.PEN))
        }
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = attempt { createInitialAccounts(drafts) }
            _uiState.update {
                when (result) {
                    is Outcome.Ok -> it.copy(saving = false, created = true)
                    is Outcome.Failed -> it.copy(saving = false, error = result.error)
                }
            }
        }
    }

    private fun fail(error: BusinessError) = _uiState.update { it.copy(error = error) }

    private companion object {
        /** Lista sugerida de S03; `DIGITAL_WALLET` no se ofrece en el primer uso. */
        val SUGGESTED = listOf("BBVA" to AccountType.BANK, "BCP" to AccountType.BANK, "Efectivo" to AccountType.CASH)
    }
}
