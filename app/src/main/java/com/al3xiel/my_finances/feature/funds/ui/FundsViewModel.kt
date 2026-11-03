package com.al3xiel.my_finances.feature.funds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundSummary
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class FundsUiState(
    val loaded: Boolean = false,
    val funds: List<FundOverview> = emptyList(),
    val summary: FundSummary = FundSummary(Money.zero(CurrencyCode.PEN), Money.zero(CurrencyCode.PEN)),
)

/** S52 (lista): solo se lee; abrir un fondo, crearlo o arquear lo resuelve la navegación. */
@HiltViewModel
class FundsViewModel @Inject constructor(
    funds: FundRepository,
) : ViewModel() {
    val uiState: StateFlow<FundsUiState> = combine(funds.observeFunds(), funds.observeSummary()) { list, summary ->
        FundsUiState(loaded = true, funds = list, summary = summary)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FundsUiState())
}
