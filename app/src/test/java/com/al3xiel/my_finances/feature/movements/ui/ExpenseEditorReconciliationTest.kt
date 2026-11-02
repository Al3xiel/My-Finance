package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** El editor de gasto abierto desde S61: propone la cuenta de la conciliación y deja el gasto vinculado a ella. */
class ExpenseEditorReconciliationTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()
    private val linkGuards = setOf(ReconciliationGuards(reconciliations))
    private val r1 = ReconciliationId("r1")

    init {
        f.accounts.balances[f.cash] = 10_000
        reconciliations.reconciliations[r1] = Reconciliation(
            r1, f.cash, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN), ReconciliationStatus.OPEN, null, null, null,
            Instant.EPOCH, null, false,
        )
    }

    private fun viewModel(id: String? = null, reconciliationId: String? = "r1") = ExpenseEditorViewModel(
        id, null, null, f.movements, f.accounts, f.categories, f.tags, FakePoolRepository(), FakeFundRepository(),
        RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids, f.writeLock, emptySet(), emptySet(), linkGuards),
        CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, emptySet(), emptySet(), emptySet(), linkGuards),
        f.clock,
        reconciliations,
        reconciliationId,
    )

    private fun fill(vm: ExpenseEditorViewModel) {
        vm.onAction(ExpenseEditorUiAction.EditTotal("3.50"))
        vm.onAction(ExpenseEditorUiAction.SelectPartCategory(vm.uiState.value.parts.single().key, f.food))
    }

    @Test
    fun proposesTheAccountBeingReconciledAndSaysItIsLinked() {
        val state = viewModel().uiState.value

        assertEquals(f.cash, state.accountId)
        assertTrue(state.linkedToReconciliation)
    }

    @Test
    fun withoutAReconciliationItKeepsTheUsualProposalAndIsNotLinked() {
        val state = viewModel(reconciliationId = null).uiState.value

        assertEquals(f.bbva, state.accountId)
        assertFalse(state.linkedToReconciliation)
    }

    @Test
    fun savingRecordsTheExpenseLinkedToTheReconciliation() {
        val vm = viewModel()
        fill(vm)

        vm.onAction(ExpenseEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(r1, (f.store.events.single().event as ExpenseRegistered).reconciliationId)
    }

    @Test
    fun anAccountOutsideTheReconciliationIsRejectedWithoutSaving() {
        val vm = viewModel()
        fill(vm)
        vm.onAction(ExpenseEditorUiAction.SelectAccount(f.bbva))

        vm.onAction(ExpenseEditorUiAction.Save)

        assertEquals(BusinessError.MovementNotInAccount, vm.uiState.value.error)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun correctingALinkedExpenseKeepsTheLink() {
        f.expenseMovement("e1", f.cash, "p1" to 500L)
        val vm = viewModel(id = "e1")
        vm.onAction(ExpenseEditorUiAction.EditTotal("7"))
        vm.onAction(ExpenseEditorUiAction.Save)
        vm.onAction(ExpenseEditorUiAction.ConfirmReason("monto mal"))

        assertTrue(vm.uiState.value.saved)
        assertEquals(r1, (f.store.events.single().event as MovementCorrected).reconciliationId)
        assertEquals(MovementId("e1"), (f.store.events.single().event as MovementCorrected).movementId)
    }
}
