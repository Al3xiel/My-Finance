package com.al3xiel.my_finances.feature.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

data class AccountDetailUiState(
    val account: AccountWithBalance? = null,
    val ledger: List<LedgerLine> = emptyList(),
    /** Fecha elegida para "saldo a una fecha"; `null` = no se pidió. */
    val asOf: LocalDate? = null,
    val balanceAsOfMinor: Long? = null,
    val today: LocalDate = LocalDate.ofEpochDay(0),
)

sealed interface AccountDetailUiAction {
    data class PickAsOf(val asOf: LocalDate) : AccountDetailUiAction
    data object ClearAsOf : AccountDetailUiAction
}

@HiltViewModel(assistedFactory = AccountDetailViewModel.Factory::class)
class AccountDetailViewModel @AssistedInject constructor(
    @Assisted accountIdValue: String,
    private val accounts: AccountRepository,
    clock: Clock,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(accountIdValue: String): AccountDetailViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val accountId = AccountId(accountIdValue)
    private val today = LocalDate.now(clock)
    private val asOf = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<AccountDetailUiState> = combine(
        accounts.observeAccount(accountId),
        accounts.observeLedger(accountId),
        asOf,
    ) { account, ledger, asOf ->
        AccountDetailUiState(
            account = account,
            ledger = ledger,
            asOf = asOf,
            balanceAsOfMinor = asOf?.let { accounts.balanceAt(accountId, it) },
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountDetailUiState(today = today))

    fun onAction(action: AccountDetailUiAction) {
        when (action) {
            is AccountDetailUiAction.PickAsOf -> asOf.value = action.asOf
            AccountDetailUiAction.ClearAsOf -> asOf.value = null
        }
    }
}
