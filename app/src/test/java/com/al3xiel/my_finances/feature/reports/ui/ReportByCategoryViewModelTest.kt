package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportByCategoryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reports = FakeReportRepository()

    private fun part(id: String, amount: Long, category: String, parent: String? = null) =
        PartRow(id, "bbva", category, parent, amount, false, 0, emptyList())

    private fun viewModel() = ReportByCategoryViewModel("2026-10-01", "2026-10-31", reports, f.categories)

    @Test fun `rows carry names, add up to the own spend and sum to 100 percent`() {
        val bus = CategoryId("bus")
        f.categories.categories[bus] = Category(bus, "Bus", f.food, false)
        reports.parts = listOf(part("a", 600, "bus", "food"), part("b", 400, "fees"))
        val state = viewModel().uiState.value
        assertTrue(state.loaded)
        assertEquals(1_000L, state.total)
        assertEquals(10_000, state.rows.sumOf { it.basisPoints })
        assertEquals(listOf("Comida", "Comisiones"), state.rows.map { it.name })
        assertEquals(listOf("Bus"), state.rows.first().children.map { it.name })
    }

    @Test fun `the detail opens and closes by tapping the category`() {
        val vm = viewModel()
        vm.onAction(ReportByCategoryUiAction.ToggleDetail("food"))
        assertEquals("food", vm.uiState.value.expandedId)
        vm.onAction(ReportByCategoryUiAction.ToggleDetail("food"))
        assertNull(vm.uiState.value.expandedId)
    }

    @Test fun `no spend gives an empty loaded report`() {
        val state = viewModel().uiState.value
        assertTrue(state.loaded && state.rows.isEmpty())
    }
}
