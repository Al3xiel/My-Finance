package com.al3xiel.my_finances.feature.accounts.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
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

/** La transferencia abierta desde S61: la cuenta que se concilia va de origen y la transferencia queda vinculada. */
class TransferEditorReconciliationTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()
    private val r1 = ReconciliationId("r1")

    init {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo")
        reconciliations.reconciliations[r1] = Reconciliation(
            r1, f.bbva, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN), ReconciliationStatus.OPEN, null, null, null,
            Instant.EPOCH, null, false,
        )
    }

    private fun viewModel(reconciliationId: String? = "r1") = TransferEditorViewModel(
        f.accounts,
        RegisterTransferUseCase(f.rules, f.accounts, f.recorder, f.ids, setOf(ReconciliationGuards(reconciliations))),
        f.clock,
        reconciliations,
        reconciliationId,
    )

    @Test
    fun theReconciledAccountIsTheOriginAndTheOtherOneTheDestination() {
        val state = viewModel().uiState.value

        assertEquals(f.bbva, state.fromId)
        assertEquals(f.cash, state.toId)
        assertTrue(state.linkedToReconciliation)
    }

    @Test
    fun theOriginIsTheReconciledAccountEvenIfItIsNotTheFirstOne() {
        reconciliations.reconciliations[r1] = reconciliations.reconciliations.getValue(r1).copy(accountId = f.cash)

        val state = viewModel().uiState.value

        assertEquals(f.cash, state.fromId)
        assertEquals(f.bbva, state.toId)
    }

    @Test
    fun savingRecordsTheTransferLinkedToTheReconciliation() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.EditAmount("5"))

        vm.onAction(TransferEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(r1, (f.store.events.single().event as TransferRegistered).reconciliationId)
    }

    @Test
    fun aTransferThatDoesNotTouchTheReconciledAccountIsRejected() {
        f.accounts.accounts[AccountIdOther] = f.account(AccountIdOther, "BCP")
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.SelectFrom(f.cash))
        vm.onAction(TransferEditorUiAction.SelectTo(AccountIdOther))
        vm.onAction(TransferEditorUiAction.EditAmount("5"))

        vm.onAction(TransferEditorUiAction.Save)

        assertFalse(vm.uiState.value.saved)
        assertEquals(BusinessError.MovementNotInAccount, vm.uiState.value.error)
    }

    @Test
    fun withoutAReconciliationNothingChanges() {
        val state = viewModel(reconciliationId = null).uiState.value

        assertFalse(state.linkedToReconciliation)
        assertEquals(f.bbva, state.fromId)
    }

    private companion object {
        val AccountIdOther = com.al3xiel.my_finances.domain.model.AccountId("bcp")
    }
}
