package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter
import com.al3xiel.my_finances.feature.movements.domain.model.MovementSummary
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.domain.model.event.TagKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MovementsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    private fun summary(
        id: String,
        type: MovementType,
        minor: Long,
        on: LocalDate,
        account: AccountId = f.bbva,
        counter: AccountId? = null,
        note: String? = null,
        status: MovementStatus = MovementStatus.ACTIVE,
    ) = MovementSummary(MovementId(id), type, account, counter, f.pen(minor), note, on, status, 1)

    private fun viewModel() = MovementsViewModel(f.movements, f.accounts, f.categories, f.tags)

    @Test
    fun groupsRowsByDayKeepingTheRepositoryOrderAndResolvesAccountNames() {
        f.movements.summaries = listOf(
            summary("m3", MovementType.TRANSFER, 100, f.today, counter = f.cash),
            summary("m2", MovementType.EXPENSE, 300, f.today, note = "Mercado"),
            summary("m1", MovementType.INCOME, 5_000, f.today.minusDays(2), status = MovementStatus.VOIDED),
        )

        val days = viewModel().uiState.value.days

        assertEquals(listOf(f.today, f.today.minusDays(2)), days.map { it.day })
        assertEquals(listOf("m3", "m2"), days[0].rows.map { it.id.value })
        assertEquals("BBVA", days[0].rows[1].accountName)
        assertEquals("Efectivo", days[0].rows[0].counterAccountName)
        assertEquals("Mercado", days[0].rows[1].note)
        assertTrue(days[1].rows.single().isVoided)
        assertEquals(f.pen(5_000), days[1].rows.single().amount)
    }

    @Test
    fun startsUnfilteredAndFiltersAreSentToTheRepository() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.isFiltered)
        assertEquals(MovementFilter(), f.movements.requestedFilters.last())

        vm.onAction(MovementsUiAction.SetAccount(f.cash))
        vm.onAction(MovementsUiAction.SetType(MovementType.EXPENSE))
        vm.onAction(MovementsUiAction.SetCategory(f.food))
        vm.onAction(MovementsUiAction.SetTag(f.tag))
        vm.onAction(MovementsUiAction.SetFrom(f.today.minusDays(10)))
        vm.onAction(MovementsUiAction.SetTo(f.today))
        vm.onAction(MovementsUiAction.SetText("mercado"))

        val expected = MovementFilter(f.cash, MovementType.EXPENSE, f.food, f.tag, f.today.minusDays(10), f.today, "mercado")
        assertEquals(expected, f.movements.requestedFilters.last())
        assertEquals(expected, vm.uiState.value.filter)
        assertTrue(vm.uiState.value.isFiltered)
    }

    @Test
    fun blankSearchTextMeansNoTextFilterAndClearResetsEverything() {
        val vm = viewModel()
        vm.onAction(MovementsUiAction.SetText("   "))
        assertNull(vm.uiState.value.filter.text)
        assertFalse(vm.uiState.value.isFiltered)

        vm.onAction(MovementsUiAction.SetAccount(f.cash))
        vm.onAction(MovementsUiAction.ClearFilters)
        assertEquals(MovementFilter(), vm.uiState.value.filter)
        assertFalse(vm.uiState.value.isFiltered)
    }

    @Test
    fun filterOptionsListOnlyActiveEntities() {
        f.accounts.accounts[AccountId("old")] = Account(AccountId("old"), "Antigua", f.account(f.bbva, "x").type, f.pen(0).currency, null, true)
        f.categories.categories[CategoryId("old")] = Category(CategoryId("old"), "Vieja", null, true)
        f.tags.tags[TagId("old")] = Tag(TagId("old"), "Viejo", TagKind.TOPIC, null, true)

        val options = viewModel().uiState.value.options

        assertEquals(listOf("BBVA", "Efectivo"), options.accounts.map { it.label }.sorted())
        assertEquals(listOf("Comida", "Comisiones"), options.categories.map { it.label }.sorted())
        assertEquals(listOf("Viaje"), options.tags.map { it.label })
    }
}
