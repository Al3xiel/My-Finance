package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IncomeEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    private fun viewModel(id: String? = null) = IncomeEditorViewModel(
        id, f.movements, f.accounts, f.sources,
        RegisterIncomeUseCase(f.rules, f.recorder, f.ids),
        CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock),
        f.clock,
    )

    @Test
    fun proposesTheFirstAccountAndTheFirstRegularSource() {
        f.sources.sources[IncomeSourceId("sys")] = IncomeSource(IncomeSourceId("sys"), "Reembolso", IncomeSourceKind.REIMBURSEMENT, null)

        val state = viewModel().uiState.value

        assertEquals(f.bbva, state.accountId)
        assertEquals(f.source, state.sourceId)
        assertEquals(listOf("Sueldo"), state.sourceOptions.map { it.label })
        assertEquals(f.today, state.occurredOn)
        assertTrue(state.loaded)
    }

    @Test
    fun savesAnIncomeAndFinishes() {
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.EditAmount("1500.5"))
        vm.onAction(IncomeEditorUiAction.EditNote("octubre"))
        vm.onAction(IncomeEditorUiAction.Save)

        assertEquals(
            IncomeRegistered(MovementId("id-1"), f.bbva, f.pen(150_050), f.source, null, "octubre"),
            f.store.events.single().event,
        )
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun reportsInvalidInputWithoutEmitting() {
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.Save)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)

        vm.onAction(IncomeEditorUiAction.EditAmount("5"))
        assertNull(vm.uiState.value.error)
        vm.onAction(IncomeEditorUiAction.SelectOccurredOn(f.today.plusDays(1)))
        vm.onAction(IncomeEditorUiAction.Save)
        assertEquals(BusinessError.FutureDate, vm.uiState.value.error)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun correctionLoadsTheIncomeAndNeedsAReason() {
        f.incomeMovement("m1", f.bbva, 500)
        val vm = viewModel("m1")

        assertTrue(vm.uiState.value.isCorrection)
        assertEquals("5", vm.uiState.value.amountText)

        vm.onAction(IncomeEditorUiAction.EditAmount("7"))
        vm.onAction(IncomeEditorUiAction.SelectAccount(f.cash))
        vm.onAction(IncomeEditorUiAction.Save)
        assertTrue(vm.uiState.value.showReasonDialog)
        assertEquals(0, f.store.events.size)

        vm.onAction(IncomeEditorUiAction.ConfirmReason("cuenta equivocada"))

        val event = f.store.events.single().event as MovementCorrected
        assertEquals(MovementSnapshot.Income(f.cash, f.pen(700), f.source, null), event.snapshot)
        assertEquals("cuenta equivocada", event.reason)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun aCorrectionThatMakesTheBalanceNegativeWarnsFirst() {
        f.incomeMovement("m1", f.bbva, 500)
        f.accounts.balances[f.bbva] = 100
        val vm = viewModel("m1")
        vm.onAction(IncomeEditorUiAction.EditAmount("2"))
        vm.onAction(IncomeEditorUiAction.Save)
        vm.onAction(IncomeEditorUiAction.ConfirmReason("monto mal"))

        assertEquals(BusinessError.WouldGoNegative(-200), vm.uiState.value.negativeWarning)
        assertFalse(vm.uiState.value.saved)

        vm.onAction(IncomeEditorUiAction.ConfirmNegativeBalance)
        assertTrue(vm.uiState.value.saved)
        assertEquals("monto mal", (f.store.events.single().event as MovementCorrected).reason)
    }

    @Test
    fun aMissingOrNonIncomeMovementIsMarkedNotFound() {
        f.expenseMovement("exp", f.bbva, "p1" to 100)
        assertTrue(viewModel("ghost").uiState.value.notFound)
        assertTrue(viewModel("exp").uiState.value.notFound)
    }

    @Test
    fun dismissingTheWarningOfACorrectionAsksForTheReasonAgain() {
        f.incomeMovement("m1", f.bbva, 500)
        f.accounts.balances[f.bbva] = 100
        val vm = viewModel("m1")
        vm.onAction(IncomeEditorUiAction.EditAmount("2"))
        vm.onAction(IncomeEditorUiAction.Save)
        vm.onAction(IncomeEditorUiAction.ConfirmReason("monto mal"))
        assertEquals(BusinessError.WouldGoNegative(-200), vm.uiState.value.negativeWarning)

        vm.onAction(IncomeEditorUiAction.DismissWarning)
        vm.onAction(IncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.showReasonDialog)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun anUnexpectedFailureWhileCorrectingShowsAnErrorAndAsksForTheReasonAgain() {
        f.incomeMovement("m1", f.bbva, 500)
        val vm = viewModel("m1")
        vm.onAction(IncomeEditorUiAction.EditAmount("7"))
        vm.onAction(IncomeEditorUiAction.Save)
        f.movements.failure = IllegalStateException("db")
        vm.onAction(IncomeEditorUiAction.ConfirmReason("monto mal"))

        assertEquals(BusinessError.Unexpected, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)

        f.movements.failure = null
        vm.onAction(IncomeEditorUiAction.Save)
        assertTrue(vm.uiState.value.showReasonDialog)
        vm.onAction(IncomeEditorUiAction.ConfirmReason("monto mal"))
        assertTrue(vm.uiState.value.saved)
    }
}
