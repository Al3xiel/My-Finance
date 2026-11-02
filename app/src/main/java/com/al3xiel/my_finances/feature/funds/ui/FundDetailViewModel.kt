package com.al3xiel.my_finances.feature.funds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.feature.funds.domain.model.FundCountRecord
import com.al3xiel.my_finances.feature.funds.domain.model.FundOverview
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.CloseFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.MarkUsageDayUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.TopUpFundUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class FundDetailUiState(
    val loaded: Boolean = false,
    val overview: FundOverview? = null,
    val today: LocalDate = LocalDate.ofEpochDay(0),
    val usageDays: List<LocalDate> = emptyList(),
    val counts: List<FundCountRecord> = emptyList(),
    /** Otros fondos activos: los destinos posibles de `ROLL_OVER`. */
    val rollOverTargets: List<FundOverview> = emptyList(),
    val notice: BusinessError? = null,
) {
    val notFound: Boolean get() = loaded && overview == null
}

sealed interface FundDetailUiAction {
    data class TopUp(val amountText: String) : FundDetailUiAction
    data class ToggleDay(val day: LocalDate) : FundDetailUiAction
    data class Close(val policy: RemainderPolicy, val rollOverToFundId: FundId?) : FundDetailUiAction
    data object DismissNotice : FundDetailUiAction
}

/** S52 (detalle): cifras, días de uso y arqueos del fondo; recargar, marcar un día y cerrar. Arquear y editar los resuelve la navegación. */
@HiltViewModel(assistedFactory = FundDetailViewModel.Factory::class)
class FundDetailViewModel @AssistedInject constructor(
    @Assisted fundIdValue: String,
    funds: FundRepository,
    private val clock: Clock,
    private val topUp: TopUpFundUseCase,
    private val markDay: MarkUsageDayUseCase,
    private val close: CloseFundUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(fundIdValue: String): FundDetailViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    private val fundId = FundId(fundIdValue)
    private val notice = MutableStateFlow<BusinessError?>(null)

    val uiState: StateFlow<FundDetailUiState> = combine(
        funds.observeFund(fundId), funds.observeUsageDays(fundId), funds.observeCounts(fundId), funds.observeFunds(), notice,
    ) { overview, days, counts, all, notice ->
        FundDetailUiState(
            loaded = true,
            overview = overview,
            today = LocalDate.now(clock),
            usageDays = days.sorted(),
            counts = counts,
            rollOverTargets = all.filter { it.fund.id != fundId && it.fund.status == FundStatus.ACTIVE },
            notice = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FundDetailUiState())

    fun onAction(action: FundDetailUiAction) {
        when (action) {
            is FundDetailUiAction.TopUp -> submit {
                val minor = MoneyInputFilter.toMinor(action.amountText) ?: return@submit Outcome.Failed(BusinessError.InvalidAmount)
                topUp(fundId, Money(minor, CurrencyCode.PEN))
            }
            is FundDetailUiAction.ToggleDay -> submit {
                markDay(fundId, action.day, marked = action.day !in uiState.value.usageDays)
            }
            is FundDetailUiAction.Close -> submit { close(fundId, action.policy, action.rollOverToFundId) }
            FundDetailUiAction.DismissNotice -> notice.value = null
        }
    }

    private fun submit(block: suspend () -> Outcome<Unit>) {
        viewModelScope.launch { notice.value = attempt(block).errorOrNull() }
    }
}
