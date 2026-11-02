package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.feature.reports.domain.model.LedgerRow
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportSummaryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeReports : ReportRepository {
        val asked = mutableListOf<ReportPeriod>()
        var income = 1_000L
        override suspend fun parts(period: ReportPeriod): List<PartRow> = emptyList()
        override suspend fun ledger(period: ReportPeriod): List<LedgerRow> = emptyList()
        override suspend fun partDetails(period: ReportPeriod) = emptyMap<String, com.al3xiel.my_finances.feature.reports.domain.model.PartDetail>()
        override suspend fun summary(period: ReportPeriod): ReportSummary {
            asked += period
            return ReportSummary(income, 200, com.al3xiel.my_finances.feature.reports.domain.model.OthersSpend(120, 20), -5)
        }
    }

    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val reports = FakeReports()
    private fun viewModel() = ReportSummaryViewModel(reports, clock)

    @Test fun `opens on the current month and loads its summary`() {
        val state = viewModel().uiState.value
        assertEquals(ReportPeriod.Month(YearMonth.of(2026, 11)), state.period)
        assertTrue(state.loaded)
        assertEquals(1_000L, state.summary?.income)
        assertEquals(100L, state.summary?.others?.pending)
    }

    @Test fun `changing the period reloads`() {
        val vm = viewModel()
        vm.onAction(ReportSummaryUiAction.SetPeriod(ReportPeriod.Year(2025)))
        assertEquals(ReportPeriod.Year(2025), vm.uiState.value.period)
        assertEquals(listOf<ReportPeriod>(ReportPeriod.Month(YearMonth.of(2026, 11)), ReportPeriod.Year(2025)), reports.asked)
    }

    @Test fun `a future period is ignored`() {
        val vm = viewModel()
        vm.onAction(ReportSummaryUiAction.SetPeriod(ReportPeriod.Month(YearMonth.of(2026, 12))))
        assertEquals(ReportPeriod.Month(YearMonth.of(2026, 11)), vm.uiState.value.period)
    }

    @Test fun `refresh reads the numbers again`() {
        val vm = viewModel()
        reports.income = 2_500
        vm.onAction(ReportSummaryUiAction.Refresh)
        assertEquals(2_500L, vm.uiState.value.summary?.income)
    }
}
