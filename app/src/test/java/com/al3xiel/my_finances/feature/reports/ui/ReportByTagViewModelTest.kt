package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReportByTagViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reports = FakeReportRepository()
    private fun viewModel() = ReportByTagViewModel("2026-10-01", "2026-10-31", reports, f.tags, f.categories)

    @Test fun `a part with two tags appears in both and the tags do not add up`() {
        reports.parts = listOf(PartRow("a", "bbva", "food", null, 100, false, 0, listOf("t1", "t2")))
        val rows = viewModel().uiState.value.rows
        assertEquals(listOf(100L, 100L), rows.map { it.amount })
        assertEquals("Viaje", rows.first { it.id == "t1" }.name)
        assertEquals(listOf("Comida" to 100L), rows.first { it.id == "t1" }.byCategory)
    }

    @Test fun `the crossing toggles`() {
        val vm = viewModel()
        vm.onAction(ReportByTagUiAction.ToggleCrossing("t1"))
        assertEquals("t1", vm.uiState.value.expandedId)
    }
}
