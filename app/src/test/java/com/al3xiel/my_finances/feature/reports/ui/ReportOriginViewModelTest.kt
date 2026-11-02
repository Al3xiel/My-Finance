package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.OriginBalanceRow
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSourceIds
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportOriginViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val origin = FakeOriginRepository()

    init {
        f.sources.sources[IncomeSourceId("s2")] = IncomeSource(IncomeSourceId("s2"), "Freelance", IncomeSourceKind.REGULAR, null)
    }

    private fun record(source: String, account: String, amount: Long, category: String? = "food") =
        OriginAttributionRecord(source, account, LedgerEntryKind.EXPENSE, category, null, amount)

    private fun viewModel() = ReportOriginViewModel("2026-10-01", "2026-10-31", origin, f.accounts, f.sources, f.categories)

    @Test fun `composition shows account and source names with percentages`() {
        origin.balances = listOf(OriginBalanceRow("bbva", "s1", 750), OriginBalanceRow("bbva", "s2", 250))
        val account = viewModel().uiState.value.accounts.single()
        assertEquals("BBVA", account.name)
        assertEquals(listOf("Sueldo", "Freelance"), account.slices.map { it.name })
        assertEquals(listOf(7500, 2500), account.slices.map { it.basisPoints })
    }

    @Test fun `the source with the largest spend is selected by default`() {
        origin.records = listOf(record("s1", "bbva", 30), record("s2", "bbva", 90))
        val state = viewModel().uiState.value
        assertEquals("s2", state.selectedSourceId)
        assertEquals(listOf(90L), state.spend.map { it.amountMinor })
        assertEquals("Comida", state.spend.single().categoryName)
        assertEquals("BBVA", state.spend.single().accountName)
    }

    @Test fun `selecting another source changes the spend rows`() {
        origin.records = listOf(record("s1", "bbva", 30), record("s2", "bbva", 90))
        val vm = viewModel()
        vm.onAction(ReportOriginUiAction.SelectSource("s1"))
        assertEquals("s1", vm.uiState.value.selectedSourceId)
        assertEquals(listOf(30L), vm.uiState.value.spend.map { it.amountMinor })
    }

    @Test fun `a negative unknown adjustment raises the shortfall warning`() {
        origin.balances = listOf(OriginBalanceRow("bbva", "s1", 500), OriginBalanceRow("bbva", OriginSourceIds.UNKNOWN_ADJUSTMENT, -50))
        val state = viewModel().uiState.value
        assertTrue(state.anyShortfall)
        assertTrue(state.accounts.single().hasShortfall)
    }

    @Test fun `no shortfall and no data gives an empty loaded state`() {
        val state = viewModel().uiState.value
        assertFalse(state.anyShortfall)
        assertTrue(state.loaded)
        assertTrue(state.accounts.isEmpty())
        assertEquals(null, state.selectedSourceId)
    }

    @Test fun `the sankey has source, account and category columns with names`() {
        origin.records = listOf(record("s1", "bbva", 30))
        val sankey = viewModel().uiState.value.sankey
        assertEquals(
            listOf("Sueldo", "BBVA", "Comida"),
            sankey.columns.map { column -> column.single().label },
        )
        assertEquals(3, sankey.columns.size)
        assertEquals(listOf(30L, 30L), sankey.links.map { it.amountMinor })
        assertEquals(SankeyNodeKind.CATEGORY, sankey.columns.last().single().node.kind)
    }

    @Test fun `loans and unidentified targets have no name of their own`() {
        origin.records = listOf(OriginAttributionRecord("s1", "bbva", LedgerEntryKind.LOAN_OUT, null, null, 40))
        val last = viewModel().uiState.value.sankey.columns.last().single()
        assertEquals(SankeyNodeKind.LOANS, last.node.kind)
        assertEquals(null, last.label)
    }
}
