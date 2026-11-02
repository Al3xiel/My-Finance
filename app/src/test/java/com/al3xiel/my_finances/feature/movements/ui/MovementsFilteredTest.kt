package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.core.MainDispatcherRule
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovementsFilteredTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    private fun viewModel(preset: MovementsPreset) = MovementsViewModel(preset, f.movements, f.accounts, f.categories, f.tags)

    @Test
    fun routeTextsBecomeTheFilter() {
        val vm = viewModel(MovementsPreset(from = "2026-10-01", to = "2026-10-31", tagId = "t1", accountId = "bbva"))
        val filter = vm.uiState.value.filter
        assertEquals(LocalDate.of(2026, 10, 1), filter.from)
        assertEquals(LocalDate.of(2026, 10, 31), filter.to)
        assertEquals("t1", filter.tagId?.value)
        assertEquals("bbva", filter.accountId?.value)
        assertTrue(vm.uiState.value.isFiltered)
    }

    @Test
    fun aParentCategoryFiltersByItselfAndItsSubcategories() {
        val bus = CategoryId("bus")
        f.categories.categories[bus] = Category(bus, "Bus", f.food, false)
        val vm = viewModel(MovementsPreset(categoryId = f.food.value))
        assertEquals(setOf(f.food, bus), vm.uiState.value.filter.categoryIds)
        assertEquals(f.food, vm.uiState.value.filter.categoryId)
    }

    @Test
    fun pickingAnotherCategoryDropsTheExpansion() {
        val vm = viewModel(MovementsPreset(categoryId = f.food.value))
        vm.onAction(MovementsUiAction.SetCategory(f.fees))
        assertEquals(MovementFilter(categoryId = f.fees), vm.uiState.value.filter)
    }
}
