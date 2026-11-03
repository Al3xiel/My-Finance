package com.al3xiel.my_finances.feature.quickentry.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import com.al3xiel.my_finances.feature.quickentry.domain.model.Conflict
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.ConfirmQuickEntryUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.LoadCatalogSnapshotUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.SuggestDefaultsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuickEntryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val pools = FakePoolRepository()
    private val funds = FakeFundRepository()
    private val people = FakePersonRepository()

    private fun viewModel() = QuickEntryViewModel(
        LoadCatalogSnapshotUseCase(f.accounts, f.categories, f.tags, pools, people, funds, f.sources),
        SuggestDefaultsUseCase(f.movements, f.accounts, f.categories, pools),
        ConfirmQuickEntryUseCase(
            RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids, f.writeLock),
            RegisterIncomeUseCase(f.rules, f.recorder, f.ids),
            pools,
            f.clock,
        ),
    )

    private fun QuickEntryViewModel.type(line: String) = onAction(QuickEntryUiAction.EditLine(line))

    @Test fun `an empty line shows nothing and cannot be saved`() {
        val state = viewModel().uiState.value
        assertFalse(state.hasLine)
        assertFalse(state.canSave)
    }

    @Test fun `understood line fills the preview`() {
        f.expenseMovement("m1", f.cash, "p1" to 100L)
        val vm = viewModel()
        vm.type("45 bbva comida")
        val s = vm.uiState.value
        assertEquals("45", s.amountText)
        assertEquals("bbva", s.accountId)
        assertFalse(s.accountSuggested)
        assertEquals("food", s.categoryId)
        assertFalse(s.categorySuggested)
        assertTrue(s.canSave)
    }

    @Test fun `missing account and category come marked as suggested`() {
        f.expenseMovement("m1", f.cash, "p1" to 100L)
        val vm = viewModel()
        vm.type("45")
        val s = vm.uiState.value
        assertEquals("cash", s.accountId)
        assertTrue(s.accountSuggested)
        assertEquals("food", s.categoryId)
        assertTrue(s.categorySuggested)
        assertTrue(s.canSave)
    }

    @Test fun `without a category suggestion it cannot be saved until one is picked`() {
        val vm = viewModel()
        vm.type("45 bbva")
        assertNull(vm.uiState.value.categoryId)
        assertFalse(vm.uiState.value.canSave)
        vm.onAction(QuickEntryUiAction.SelectCategory("food"))
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun `a conflict blocks saving until the user picks`() {
        val vm = viewModel()
        vm.type("45 bbva efectivo comida")
        val s = vm.uiState.value
        assertEquals(setOf(Conflict.ACCOUNTS), s.conflicts)
        assertFalse(s.canSave)
        vm.onAction(QuickEntryUiAction.SelectAccount("cash"))
        assertTrue(vm.uiState.value.conflicts.isEmpty())
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun `an ambiguous token must be chosen`() {
        f.categories.categories[com.al3xiel.my_finances.domain.model.CategoryId("c2")] =
            com.al3xiel.my_finances.feature.categories.domain.model.Category(com.al3xiel.my_finances.domain.model.CategoryId("c2"), "Comidas fuera", null, false)
        val vm = viewModel()
        vm.type("45 bbva com")
        val s = vm.uiState.value
        assertEquals(1, s.ambiguities.size)
        assertFalse(s.canSave)
        val ambiguity = s.ambiguities.single()
        vm.onAction(QuickEntryUiAction.Choose(ambiguity, ambiguity.candidates.first { it.id == "food" }))
        assertTrue(vm.uiState.value.ambiguities.isEmpty())
        assertEquals("food", vm.uiState.value.categoryId)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun `saving an expense registers it and finishes`() {
        val vm = viewModel()
        vm.type("3,5 bbva comida huevos")
        vm.onAction(QuickEntryUiAction.Save)
        assertTrue(vm.uiState.value.saved)
        val event = f.store.events.single().event as ExpenseRegistered
        assertEquals(f.pen(350), event.total)
        assertEquals("huevos", event.note)
    }

    @Test fun `saving an income registers it`() {
        val vm = viewModel()
        vm.type("+50 bbva sueldo")
        assertTrue(vm.uiState.value.isIncome)
        assertEquals("s1", vm.uiState.value.sourceId)
        vm.onAction(QuickEntryUiAction.Save)
        assertTrue(vm.uiState.value.saved)
        assertEquals(f.pen(5000), (f.store.events.single().event as IncomeRegistered).amount)
    }

    @Test fun `an income without a source cannot be saved`() {
        val vm = viewModel()
        vm.type("+50 bbva")
        assertFalse(vm.uiState.value.canSave)
        vm.onAction(QuickEntryUiAction.SelectSource("s1"))
        assertTrue(vm.uiState.value.canSave)
    }

    @Test fun `negative balance asks for confirmation before saving`() {
        val vm = viewModel()
        vm.type("20 bbva comida")
        vm.onAction(QuickEntryUiAction.Save)
        assertTrue(vm.uiState.value.negativeWarning != null)
        assertFalse(vm.uiState.value.saved)
        assertTrue(f.store.events.isEmpty())
        vm.onAction(QuickEntryUiAction.ConfirmNegativeBalance)
        assertTrue(vm.uiState.value.saved)
    }

    @Test fun `a rejected save shows the error and an edit clears it`() {
        val vm = viewModel()
        vm.type("0 bbva comida")
        vm.onAction(QuickEntryUiAction.Save)
        assertTrue(vm.uiState.value.error is BusinessError)
        vm.onAction(QuickEntryUiAction.EditAmount("5"))
        assertNull(vm.uiState.value.error)
    }

    @Test fun `a pool brings its default category as a suggestion`() {
        val dogs = PoolId("dogs")
        pools.pools[dogs] = Pool(dogs, "Perros", PersonId("mom"), PoolKind.PERMANENT, f.fees, emptyList(), false)
        val vm = viewModel()
        vm.type("10 bbva perros")
        val s = vm.uiState.value
        assertEquals("dogs", s.poolId)
        assertEquals("fees", s.categoryId)
        assertTrue(s.categorySuggested)
    }

    @Test fun `save is ignored while something is unresolved even if a category is suggested`() {
        f.expenseMovement("m1", f.cash, "p1" to 100L)
        val vm = viewModel()
        vm.type("5 bbva efectivo")
        assertFalse(vm.uiState.value.canSave)
        vm.onAction(QuickEntryUiAction.Save)
        assertFalse(vm.uiState.value.saved)
        assertTrue(f.store.events.isEmpty())
    }

    @Test fun `a wrongly understood pool can be cleared`() {
        val dogs = PoolId("dogs")
        pools.pools[dogs] = Pool(dogs, "Perros", PersonId("mom"), PoolKind.PERMANENT, f.fees, emptyList(), false)
        val vm = viewModel()
        vm.type("10 bbva comida perros")
        assertEquals("dogs", vm.uiState.value.poolId)
        vm.onAction(QuickEntryUiAction.ClearPool)
        assertNull(vm.uiState.value.poolId)
        assertTrue(vm.uiState.value.canSave)
    }
}
