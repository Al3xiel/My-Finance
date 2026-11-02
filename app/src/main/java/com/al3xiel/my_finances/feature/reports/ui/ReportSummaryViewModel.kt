package com.al3xiel.my_finances.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class ReportSummaryUiState(
    val period: ReportPeriod,
    val today: LocalDate,
    val summary: ReportSummary? = null,
) {
    val loaded: Boolean get() = summary != null
}

sealed interface ReportSummaryUiAction {
    data class SetPeriod(val period: ReportPeriod) : ReportSummaryUiAction

    /** Al volver a la pestaña: los reportes son consultas, no se actualizan solos. */
    data object Refresh : ReportSummaryUiAction
}

/** S40: el resumen del periodo (mes por defecto, D36). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportSummaryViewModel @Inject constructor(
    private val reports: ReportRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val period = MutableStateFlow<ReportPeriod>(ReportPeriod.Month(YearMonth.from(today)))
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<ReportSummaryUiState> = combine(period, refresh) { p, _ -> p }
        .flatMapLatest { p -> flow { emit(ReportSummaryUiState(p, today, reports.summary(p))) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReportSummaryUiState(period.value, today))

    fun onAction(action: ReportSummaryUiAction) {
        when (action) {
            is ReportSummaryUiAction.SetPeriod -> if (!action.period.isFuture(today)) period.value = action.period
            ReportSummaryUiAction.Refresh -> refresh.value += 1
        }
    }
}
