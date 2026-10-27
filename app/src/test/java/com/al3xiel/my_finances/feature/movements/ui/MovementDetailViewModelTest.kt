package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.feature.movements.domain.model.HistoryKind
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementHistoryEntry
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class MovementDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    private fun viewModel(id: String) =
        MovementDetailViewModel(id, f.movements, f.accounts, f.categories, f.tags, f.sources, VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock))

    private fun show(movement: Movement, vararg history: HistoryKind) {
        f.movements.details[movement.id] = MovementDetail(
            movement,
            history.mapIndexed { i, kind -> MovementHistoryEntry(i + 1L, kind, f.today, Instant.EPOCH, null) },
        )
    }

    @Test
    fun resolvesTheNamesOfEverythingTheMovementReferences() {
        show(f.expenseMovement("m1", f.bbva, "p1" to 300), HistoryKind.REGISTERED)

        val state = viewModel("m1").uiState.value

        assertTrue(state.loaded)
        assertEquals("m1", state.detail?.movement?.id?.value)
        assertEquals("BBVA", state.names.accounts["bbva"])
        assertEquals("Comida", state.names.categories["food"])
        assertEquals("Viaje", state.names.tags["t1"])
        assertEquals("Sueldo", state.names.incomeSources["s1"])
        assertTrue(state.canEdit)
    }

    @Test
    fun transfersAndVoidedMovementsCannotBeCorrectedOrVoided() {
        show(f.transferMovement("tr"), HistoryKind.REGISTERED)
        show(f.expenseMovement("v", f.bbva, "p1" to 100, status = MovementStatus.VOIDED), HistoryKind.REGISTERED, HistoryKind.VOIDED)

        assertFalse(viewModel("tr").uiState.value.canEdit)
        assertFalse(viewModel("v").uiState.value.canEdit)
    }

    @Test
    fun aMissingMovementIsLoadedButEmpty() {
        val state = viewModel("ghost").uiState.value
        assertTrue(state.loaded)
        assertNull(state.detail)
        assertFalse(state.canEdit)
    }

    @Test
    fun voidingAsksForTheReasonAndEmitsTheEvent() {
        show(f.expenseMovement("m1", f.bbva, "p1" to 300), HistoryKind.REGISTERED)
        val vm = viewModel("m1")

        vm.onAction(MovementDetailUiAction.ShowVoidDialog)
        assertTrue(vm.uiState.value.showVoidDialog)

        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))

        assertEquals(MovementVoided(MovementId("m1"), "duplicado", null), f.store.events.single().event)
        assertFalse(vm.uiState.value.showVoidDialog)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun aRejectedVoidKeepsTheDialogClosedAndShowsTheError() {
        show(f.transferMovement("tr"), HistoryKind.REGISTERED)
        val vm = viewModel("tr")

        vm.onAction(MovementDetailUiAction.ShowVoidDialog)
        vm.onAction(MovementDetailUiAction.ConfirmVoid("x"))

        assertEquals(BusinessError.TransferNotEditable, vm.uiState.value.error)
        assertFalse(vm.uiState.value.showVoidDialog)
        assertEquals(0, f.store.events.size)

        vm.onAction(MovementDetailUiAction.DismissError)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun dismissClosesTheVoidDialog() {
        show(f.expenseMovement("m1", f.bbva, "p1" to 300), HistoryKind.REGISTERED)
        val vm = viewModel("m1")
        vm.onAction(MovementDetailUiAction.ShowVoidDialog)
        vm.onAction(MovementDetailUiAction.DismissVoidDialog)
        assertFalse(vm.uiState.value.showVoidDialog)
    }

    @Test
    fun voidingAnIncomeThatLeavesTheBalanceNegativeWarnsAndKeepsTheReasonForTheConfirmation() {
        show(f.incomeMovement("m1", f.bbva, 1_500), HistoryKind.REGISTERED)
        val vm = viewModel("m1")

        vm.onAction(MovementDetailUiAction.ShowVoidDialog)
        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))

        assertEquals(BusinessError.WouldGoNegative(-500), vm.uiState.value.negativeWarning)
        assertFalse(vm.uiState.value.showVoidDialog)
        assertEquals(0, f.store.events.size)

        vm.onAction(MovementDetailUiAction.ConfirmNegativeBalance)

        assertEquals(MovementVoided(MovementId("m1"), "duplicado", null), f.store.events.single().event)
        assertNull(vm.uiState.value.negativeWarning)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun dismissingTheWarningForgetsTheReasonSoConfirmingDoesNothing() {
        show(f.incomeMovement("m1", f.bbva, 1_500), HistoryKind.REGISTERED)
        val vm = viewModel("m1")
        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))
        vm.onAction(MovementDetailUiAction.DismissWarning)
        vm.onAction(MovementDetailUiAction.ConfirmNegativeBalance)

        assertNull(vm.uiState.value.negativeWarning)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun anUnexpectedFailureWhileVoidingShowsAnErrorAndAllowsTryingAgain() {
        show(f.expenseMovement("m1", f.bbva, "p1" to 300), HistoryKind.REGISTERED)
        val vm = viewModel("m1")
        f.movements.failure = IllegalStateException("db")

        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))
        assertEquals(BusinessError.Unexpected, vm.uiState.value.error)
        assertEquals(0, f.store.events.size)

        f.movements.failure = null
        vm.onAction(MovementDetailUiAction.DismissError)
        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun aSecondConfirmWhileTheFirstIsStillRunningDoesNotVoidTwice() {
        show(f.expenseMovement("m1", f.bbva, "p1" to 300), HistoryKind.REGISTERED)
        val vm = viewModel("m1")
        val gate = CompletableDeferred<Unit>()
        f.movements.gate = gate

        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))
        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))
        gate.complete(Unit)

        assertEquals(1, f.store.events.size)
    }

    @Test
    fun confirmingTheWarningWhileASubmitIsRunningKeepsTheReasonAndSendsNothingElse() {
        show(f.incomeMovement("m1", f.bbva, 1_500), HistoryKind.REGISTERED)
        val vm = viewModel("m1")
        vm.onAction(MovementDetailUiAction.ConfirmVoid("duplicado"))
        assertEquals(BusinessError.WouldGoNegative(-500), vm.uiState.value.negativeWarning)

        val gate = CompletableDeferred<Unit>()
        f.movements.gate = gate
        vm.onAction(MovementDetailUiAction.ConfirmNegativeBalance)
        vm.onAction(MovementDetailUiAction.ConfirmNegativeBalance)
        gate.complete(Unit)

        assertEquals(1, f.store.events.size)
    }
}
