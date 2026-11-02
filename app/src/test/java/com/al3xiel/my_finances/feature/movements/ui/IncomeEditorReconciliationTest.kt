package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ConfirmExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.RegisterIncomeWithTopUpUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

/** El editor de ingreso abierto desde S61: sin "apartar en fondo" ni "confirma ingreso esperado" (D34). */
class IncomeEditorReconciliationTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val expected = FakeExpectedIncomeRepository()
    private val reconciliations = FakeReconciliationRepository()
    private val linkGuards = setOf(ReconciliationGuards(reconciliations))
    private val r1 = ReconciliationId("r1")

    init {
        funds.put(FundId("fare"), "Pasajes")
        reconciliations.reconciliations[r1] = Reconciliation(
            r1, f.cash, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN), ReconciliationStatus.OPEN, null, null, null,
            Instant.EPOCH, null, false,
        )
    }

    private fun viewModel(id: String? = null, expectedId: String? = null, reconciliationId: String? = "r1") = IncomeEditorViewModel(
        id, expectedId, f.movements, f.accounts, f.sources, funds,
        RegisterIncomeUseCase(f.rules, f.recorder, f.ids, linkGuards),
        RegisterIncomeWithTopUpUseCase(f.rules, funds, f.recorder, f.ids, f.writeLock),
        CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, emptySet(), emptySet(), emptySet(), linkGuards),
        expected,
        ConfirmExpectedIncomeUseCase(f.rules, expected, funds, f.recorder, f.ids, f.writeLock),
        f.clock,
        reconciliations,
        reconciliationId,
    )

    @Test
    fun proposesTheAccountBeingReconciledAndHidesFundsAndExpectedIncomes() {
        expected.put("e1", f.cash, f.source, estimated = 200_000)

        val state = viewModel().uiState.value

        assertEquals(f.cash, state.accountId)
        assertTrue(state.linkedToReconciliation)
        assertTrue(state.fundOptions.isEmpty())
        assertTrue(state.suggestions.isEmpty())
        assertNull(state.selectedExpected)
    }

    @Test
    fun withoutAReconciliationFundsAndSuggestionsStillShow() {
        expected.put("e1", f.bbva, f.source, estimated = 200_000)

        val state = viewModel(reconciliationId = null).uiState.value

        assertFalse(state.linkedToReconciliation)
        assertEquals(listOf("Pasajes"), state.fundOptions.map { it.label })
        assertEquals(1, state.suggestions.size)
    }

    @Test
    fun savingRecordsTheIncomeLinkedAndWithoutAnyTopUp() {
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.EditAmount("10"))
        vm.onAction(IncomeEditorUiAction.SelectTopUpFund(FundId("fare")))

        vm.onAction(IncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        val event = f.store.events.single().event as IncomeRegistered
        assertEquals(r1, event.reconciliationId)
        assertEquals(1, f.store.events.size) // sin FundToppedUp
    }

    @Test
    fun aPresetExpectedIncomeIsIgnoredWhenLinked() {
        expected.put("e1", f.bbva, f.source, estimated = 200_000)

        val state = viewModel(expectedId = "e1").uiState.value

        assertTrue(state.loaded)
        assertEquals(f.cash, state.accountId)
        assertEquals("", state.amountText)
    }

    @Test
    fun correctingALinkedIncomeKeepsTheLink() {
        f.incomeMovement("i1", f.cash, 500)
        val vm = viewModel(id = "i1")
        vm.onAction(IncomeEditorUiAction.EditAmount("7"))
        vm.onAction(IncomeEditorUiAction.Save)
        vm.onAction(IncomeEditorUiAction.ConfirmReason("monto mal"))

        assertTrue(vm.uiState.value.saved)
        assertEquals(r1, (f.store.events.single().event as MovementCorrected).reconciliationId)
    }
}
