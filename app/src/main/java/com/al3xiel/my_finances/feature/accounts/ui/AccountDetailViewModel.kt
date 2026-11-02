package com.al3xiel.my_finances.feature.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.feature.accounts.domain.model.AccountWithBalance
import com.al3xiel.my_finances.feature.accounts.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
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
    /** Una cuenta archivada ya no se concilia. */
    val canReconcile: Boolean = false,
    /** La más reciente, de cualquier estado; con `needsReview` pide revisar un ajuste que ya no corresponde. */
    val latestReconciliation: Reconciliation? = null,
    val latestReconciledOn: LocalDate? = null,
    /** Con una abierta, el botón sigue esa conciliación en vez de empezar otra. */
    val hasOpenReconciliation: Boolean = false,
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
    reconciliations: ReconciliationRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(accountIdValue: String): AccountDetailViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val accountId = AccountId(accountIdValue)
    private val today = LocalDate.now(clock)
    private val zone = clock.zone
    private val asOf = MutableStateFlow<LocalDate?>(null)

    val uiState: StateFlow<AccountDetailUiState> = combine(
        accounts.observeAccount(accountId),
        accounts.observeLedger(accountId),
        asOf,
        reconciliations.observeLatest(accountId),
    ) { account, ledger, asOf, latest ->
        AccountDetailUiState(
            account = account,
            ledger = ledger,
            asOf = asOf,
            balanceAsOfMinor = asOf?.let { accounts.balanceAt(accountId, it) },
            today = today,
            canReconcile = account?.account?.isArchived == false,
            latestReconciliation = latest,
            latestReconciledOn = latest?.checkedAt?.atZone(zone)?.toLocalDate(),
            hasOpenReconciliation = latest?.status == ReconciliationStatus.OPEN,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AccountDetailUiState(today = today))

    fun onAction(action: AccountDetailUiAction) {
        when (action) {
            is AccountDetailUiAction.PickAsOf -> asOf.value = action.asOf
            AccountDetailUiAction.ClearAsOf -> asOf.value = null
        }
    }
}
