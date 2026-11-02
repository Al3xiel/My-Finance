package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.rules.FundExpenseGuard
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpenseEditorFundTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val pools = FakePoolRepository()
    private val funds = FakeFundRepository()
    private val fare = FundId("fare")
    private val dogs = PoolId("dogs")

    private fun viewModel(id: String? = null, presetFundId: String? = null) = ExpenseEditorViewModel(
        id, null, presetFundId, f.movements, f.accounts, f.categories, f.tags, pools, funds,
        RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids, f.writeLock, emptySet(), setOf(FundExpenseGuard(funds))),
        CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, emptySet(), emptySet(), setOf(FundExpenseGuard(funds))),
        f.clock,
        com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository(),
    )

    @Test
    fun aPresetFundIsSelectedOnTheFirstPartAndOnlyActiveFundsAreOffered() {
        funds.put(fare, "Pasajes", allocated = 5_000)
        funds.put(FundId("old"), "Viejo", status = FundStatus.CLOSED)

        val state = viewModel(presetFundId = "fare").uiState.value

        assertEquals(fare, state.parts.single().fundId)
        assertEquals(listOf("Pasajes"), state.fundOptions.map { it.label })
    }

    @Test
    fun aClosedPresetFundIsIgnored() {
        funds.put(fare, "Pasajes", status = FundStatus.CLOSED)

        assertEquals(null, viewModel(presetFundId = "fare").uiState.value.parts.single().fundId)
    }

    @Test
    fun aPartHasAFundOrAPoolNeverBoth() {
        funds.put(fare, "Pasajes")
        pools.pools[dogs] = Pool(dogs, "Perros", PersonId("mom"), PoolKind.PERMANENT, null, emptyList(), false)
        val vm = viewModel()
        val key = vm.uiState.value.parts.single().key

        vm.onAction(ExpenseEditorUiAction.SelectPartPool(key, dogs))
        vm.onAction(ExpenseEditorUiAction.SelectPartFund(key, fare))
        assertEquals(null, vm.uiState.value.parts.single().poolId)
        assertEquals(fare, vm.uiState.value.parts.single().fundId)

        vm.onAction(ExpenseEditorUiAction.SelectPartPool(key, dogs))
        assertEquals(dogs, vm.uiState.value.parts.single().poolId)
        assertEquals(null, vm.uiState.value.parts.single().fundId)
    }

    @Test
    fun savesTheExpenseWithItsFund() {
        funds.put(fare, "Pasajes", allocated = 5_000)
        val vm = viewModel(presetFundId = "fare")
        vm.onAction(ExpenseEditorUiAction.EditTotal("3.50"))
        vm.onAction(ExpenseEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(listOf(fare), (f.store.events.single().event as ExpenseRegistered).parts.map { it.fundId })
    }

    @Test
    fun spendingMoreThanTheRemainingWarnsButStillSaves() {
        funds.put(fare, "Pasajes", allocated = 500)
        val vm = viewModel(presetFundId = "fare")

        vm.onAction(ExpenseEditorUiAction.EditTotal("3"))
        assertTrue(vm.uiState.value.overspentFunds.isEmpty())

        vm.onAction(ExpenseEditorUiAction.EditTotal("8"))
        assertEquals(listOf("Pasajes"), vm.uiState.value.overspentFunds)

        vm.onAction(ExpenseEditorUiAction.Save)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun correctingKeepsTheFundOfThePartAndDoesNotCountItsOwnAmountAgainstTheRemaining() {
        funds.put(fare, "Pasajes", allocated = 1_000, detailed = 800)
        f.expenseMovement("m1", f.bbva, "p1" to 800L).let {
            f.movements.movements[it.id] = it.copy(parts = it.parts.map { part -> part.copy(fundId = fare) })
        }

        val vm = viewModel(id = "m1")

        assertEquals(fare, vm.uiState.value.parts.single().fundId)
        // El gasto ya está dentro de lo detallado: dejarlo igual no excede nada.
        assertTrue(vm.uiState.value.overspentFunds.isEmpty())
        vm.onAction(ExpenseEditorUiAction.EditTotal("11"))
        assertEquals(listOf("Pasajes"), vm.uiState.value.overspentFunds)
    }
}
