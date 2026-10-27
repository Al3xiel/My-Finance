package com.al3xiel.my_finances.feature.pools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PersonReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ReceivablesUiState(
    val total: Money = Money.zero(CurrencyCode.PEN),
    val people: List<PersonReceivable> = emptyList(),
    val pools: List<PoolReceivable> = emptyList(),
    val loans: List<LoanReceivable> = emptyList(),
) {
    val isEmpty: Boolean get() = people.isEmpty() && pools.isEmpty() && loans.isEmpty()
}

/** S30: solo se lee; abrir una persona, un cúmulo o una deuda lo resuelve la navegación. */
@HiltViewModel
class ReceivablesViewModel @Inject constructor(
    receivables: ReceivablesRepository,
) : ViewModel() {
    val uiState: StateFlow<ReceivablesUiState> = receivables.observeReceivables()
        .map { data ->
            ReceivablesUiState(
                total = data.total,
                people = data.people,
                // Primero lo que más tiene pendiente en el periodo abierto: es lo próximo que se rinde.
                pools = data.pools.sortedWith(compareByDescending<PoolReceivable> { it.periodPending.amountMinor }.thenBy { it.name.lowercase() }),
                loans = data.loans,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReceivablesUiState())
}
