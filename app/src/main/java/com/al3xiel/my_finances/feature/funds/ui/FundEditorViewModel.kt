package com.al3xiel.my_finances.feature.funds.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.MoneyInputFilter
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.feature.funds.domain.repository.FundRepository
import com.al3xiel.my_finances.feature.funds.domain.rules.FundRules
import com.al3xiel.my_finances.feature.funds.domain.usecase.CreateFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.EditFundUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

data class FundEditorUiState(
    val loaded: Boolean = false,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val mode: ConsumptionMode = ConsumptionMode.DETAILED,
    val estimateText: String = "",
    val startsOn: LocalDate = LocalDate.ofEpochDay(0),
    val endsOn: LocalDate? = null,
    /** El modo no cambia con días de uso o arqueos; el inicio no cambia con gastos asignados. */
    val modeLocked: Boolean = false,
    val startLocked: Boolean = false,
    val error: BusinessError? = null,
    val saved: Boolean = false,
)

sealed interface FundEditorUiAction {
    data class EditName(val name: String) : FundEditorUiAction
    data class SelectMode(val mode: ConsumptionMode) : FundEditorUiAction
    data class EditEstimate(val text: String) : FundEditorUiAction
    data class SetStartsOn(val day: LocalDate) : FundEditorUiAction
    data class SetEndsOn(val day: LocalDate?) : FundEditorUiAction
    data object Save : FundEditorUiAction
}

/** S54: crea un fondo o edita uno existente; los campos bloqueados por su historia se muestran sin poder cambiarse. */
@HiltViewModel(assistedFactory = FundEditorViewModel.Factory::class)
class FundEditorViewModel @AssistedInject constructor(
    @Assisted fundIdValue: String?,
    private val funds: FundRepository,
    clock: Clock,
    private val createFund: CreateFundUseCase,
    private val editFund: EditFundUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(fundIdValue: String?): FundEditorViewModel
    }

    private val fundId = fundIdValue?.let(::FundId)
    private val form = MutableStateFlow(FundEditorUiState(isEditing = fundId != null, startsOn = LocalDate.now(clock)))
    private var submitting = false

    val uiState: StateFlow<FundEditorUiState> = form.map { it }.stateIn(viewModelScope, SharingStarted.Eagerly, form.value)

    init {
        viewModelScope.launch {
            val id = fundId
            if (id == null) {
                form.update { it.copy(loaded = true) }
                return@launch
            }
            val overview = funds.find(id)
            if (overview == null) {
                form.update { it.copy(loaded = true, notFound = true) }
                return@launch
            }
            val facts = funds.editFacts(id)
            val locks = FundRules.editLocks(facts.hasUsageDays, facts.hasCounts, facts.hasAssignedExpenses, facts.latestActivityOn)
            val fund = overview.fund
            form.update {
                it.copy(
                    loaded = true,
                    name = fund.name,
                    mode = fund.mode,
                    estimateText = fund.dailyEstimate?.amountMinor?.let(MoneyInputFilter::fromMinor).orEmpty(),
                    startsOn = fund.startsOn,
                    endsOn = fund.endsOn,
                    modeLocked = locks.mode,
                    startLocked = locks.startsOn,
                )
            }
        }
    }

    fun onAction(action: FundEditorUiAction) {
        when (action) {
            is FundEditorUiAction.EditName -> edit { copy(name = action.name) }
            is FundEditorUiAction.SelectMode -> edit { copy(mode = action.mode) }
            is FundEditorUiAction.EditEstimate -> edit { copy(estimateText = action.text) }
            is FundEditorUiAction.SetStartsOn -> edit { copy(startsOn = action.day) }
            is FundEditorUiAction.SetEndsOn -> edit { copy(endsOn = action.day) }
            FundEditorUiAction.Save -> save()
        }
    }

    private fun edit(change: FundEditorUiState.() -> FundEditorUiState) = form.update { it.change().copy(error = null) }

    private fun save() {
        val current = form.value
        if (submitting) return
        submitting = true
        val estimate = MoneyInputFilter.toMinor(current.estimateText)?.let { Money(it, CurrencyCode.PEN) }
        viewModelScope.launch {
            val id = fundId
            val error = if (id != null) {
                attempt { editFund(id, current.name, current.mode, estimate, current.startsOn, current.endsOn) }.errorOrNull()
            } else {
                attempt { createFund(current.name, current.mode, estimate, current.startsOn, current.endsOn) }.errorOrNull()
            }
            submitting = false
            form.update { if (error == null) it.copy(saved = true) else it.copy(error = error) }
        }
    }
}
