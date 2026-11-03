package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reports.domain.model.LedgerRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportByAccountViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reports = FakeReportRepository()

    @Test fun `rows show names with inflow, outflow and transfers apart`() {
        reports.ledger = listOf(
            LedgerRow("bbva", LedgerEntryKind.INCOME, 500), LedgerRow("bbva", LedgerEntryKind.EXPENSE, -120),
            LedgerRow("bbva", LedgerEntryKind.TRANSFER_OUT, -100), LedgerRow("cash", LedgerEntryKind.TRANSFER_IN, 100),
        )
        val state = ReportByAccountViewModel("2026-10-01", "2026-10-31", reports, f.accounts).uiState.value
        assertEquals(listOf("BBVA", "Efectivo"), state.rows.map { it.name })
        val bbva = state.rows.first()
        assertEquals(500L, bbva.inflow)
        assertEquals(120L, bbva.outflow)
        assertEquals(-100L, bbva.transfersNet)
        assertEquals(100L, state.rows.last().transfersNet)
    }
}
