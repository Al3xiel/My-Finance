package com.al3xiel.my_finances.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class AccountFlowRowUi(val id: String, val name: String, val inflow: Long, val outflow: Long, val transfersNet: Long)

data class ReportByAccountUiState(
    val period: ReportPeriod,
    val rows: List<AccountFlowRowUi> = emptyList(),
    val loaded: Boolean = false,
)

/** S43: por cuenta, entradas, salidas y transferencias netas aparte (docs/04 §5.6). */
@HiltViewModel(assistedFactory = ReportByAccountViewModel.Factory::class)
class ReportByAccountViewModel @AssistedInject constructor(
    @Assisted("from") from: String,
    @Assisted("to") to: String,
    reports: ReportRepository,
    accounts: AccountRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("from") from: String, @Assisted("to") to: String): ReportByAccountViewModel
    }

    private val period = ReportPeriod.fromTexts(from, to)

    val uiState: StateFlow<ReportByAccountUiState> = combine(
        flow { emit(ReportCalculator.byAccount(reports.ledger(period))) },
        accounts.observeAccounts(),
    ) { flows, accountList ->
        val names = accountList.associate { it.account.id.value to it.account.name }
        ReportByAccountUiState(
            period,
            flows.map { AccountFlowRowUi(it.accountId, names[it.accountId] ?: it.accountId, it.inflow, it.outflow, it.transfersNet) }
                .sortedBy { it.name },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReportByAccountUiState(period))
}
