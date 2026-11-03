package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.feature.movements.domain.model.HistoryKind
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementHistoryEntry
import com.al3xiel.my_finances.feature.movements.domain.model.MovementSummary
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MovementsDayActionsTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    private fun summary(
        id: String,
        type: MovementType,
        minor: Long,
        on: LocalDate = f.today,
        status: MovementStatus = MovementStatus.ACTIVE,
        counter: AccountId? = null,
    ) = MovementSummary(MovementId(id), type, f.bbva, counter, f.pen(minor), null, on, status, 1)

    private fun viewModel() = MovementsViewModel(
        MovementsPreset(), f.movements, f.accounts, f.categories, f.tags,
        VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock),
    )

    @Test
    fun eachDayCarriesTheNetOfItsActiveMovementsAndLeavesOutVoidedOnesAndTransfers() {
        f.movements.summaries = listOf(
            summary("i", MovementType.INCOME, 5_000),
            summary("e", MovementType.EXPENSE, 1_250),
            summary("t", MovementType.TRANSFER, 900, counter = f.cash),
            summary("v", MovementType.EXPENSE, 700, status = MovementStatus.VOIDED),
            summary("old", MovementType.EXPENSE, 300, on = f.today.minusDays(1)),
        )

        val days = viewModel().uiState.value.days

        assertEquals(3_750L, days[0].subtotal.amountMinor)
        assertEquals(-300L, days[1].subtotal.amountMinor)
    }

    @Test
    fun onlyActiveIncomesAndExpensesCanBeCorrectedAndAllButTransfersAndVoidedCanBeVoided() {
        f.movements.summaries = listOf(
            summary("e", MovementType.EXPENSE, 100),
            summary("r", MovementType.REIMBURSEMENT, 100),
            summary("t", MovementType.TRANSFER, 100, counter = f.cash),
            summary("v", MovementType.INCOME, 100, status = MovementStatus.VOIDED),
        )

        val rows = viewModel().uiState.value.days.single().rows.associateBy { it.id.value }

        assertTrue(rows.getValue("e").canCorrect && rows.getValue("e").canVoid)
        assertTrue(!rows.getValue("r").canCorrect && rows.getValue("r").canVoid)
        assertTrue(!rows.getValue("t").canCorrect && !rows.getValue("t").canVoid)
        assertTrue(!rows.getValue("v").canCorrect && !rows.getValue("v").canVoid)
    }

    @Test
    fun voidingFromTheListAsksForTheReasonAndEmitsTheEvent() {
        f.movements.details[MovementId("m1")] = MovementDetail(
            f.expenseMovement("m1", f.bbva, "p1" to 300),
            listOf(MovementHistoryEntry(1, HistoryKind.REGISTERED, f.today, Instant.EPOCH, null)),
        )
        f.movements.summaries = listOf(summary("m1", MovementType.EXPENSE, 300))
        val vm = viewModel()

        vm.onAction(MovementsUiAction.AskVoid(MovementId("m1")))
        assertEquals(MovementId("m1"), vm.uiState.value.voidingId)

        vm.onAction(MovementsUiAction.ConfirmVoid("duplicado"))

        assertEquals(MovementVoided(MovementId("m1"), "duplicado", null), f.store.events.single().event)
        assertNull(vm.uiState.value.voidingId)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun aRejectedVoidClosesTheDialogAndShowsTheBusinessError() {
        f.movements.details[MovementId("tr")] = MovementDetail(f.transferMovement("tr"), emptyList())
        val vm = viewModel()

        vm.onAction(MovementsUiAction.AskVoid(MovementId("tr")))
        vm.onAction(MovementsUiAction.ConfirmVoid("x"))

        assertEquals(BusinessError.TransferNotEditable, vm.uiState.value.error)
        assertNull(vm.uiState.value.voidingId)
        assertEquals(0, f.store.events.size)

        vm.onAction(MovementsUiAction.DismissError)
        assertNull(vm.uiState.value.error)
    }

    private fun show(detail: com.al3xiel.my_finances.feature.movements.domain.model.Movement) {
        f.movements.details[detail.id] = MovementDetail(
            detail,
            listOf(MovementHistoryEntry(1, HistoryKind.REGISTERED, f.today, Instant.EPOCH, null)),
        )
    }

    @Test
    fun voidingAnIncomeThatLeavesTheBalanceNegativeWarnsHidesTheReasonDialogAndConfirms() {
        show(f.incomeMovement("m1", f.bbva, 1_500))
        val vm = viewModel()

        vm.onAction(MovementsUiAction.AskVoid(MovementId("m1")))
        vm.onAction(MovementsUiAction.ConfirmVoid("duplicado"))

        assertEquals(BusinessError.WouldGoNegative(-500), vm.uiState.value.negativeWarning)
        // Con el aviso a la vista no queda el diálogo de motivo debajo.
        assertNull(vm.uiState.value.voidingId)
        assertEquals(0, f.store.events.size)

        vm.onAction(MovementsUiAction.ConfirmNegativeBalance)

        assertEquals(MovementVoided(MovementId("m1"), "duplicado", null), f.store.events.single().event)
        assertNull(vm.uiState.value.negativeWarning)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun dismissingTheNegativeWarningVoidsNothing() {
        show(f.incomeMovement("m1", f.bbva, 1_500))
        val vm = viewModel()
        vm.onAction(MovementsUiAction.AskVoid(MovementId("m1")))
        vm.onAction(MovementsUiAction.ConfirmVoid("duplicado"))

        vm.onAction(MovementsUiAction.DismissWarning)

        assertNull(vm.uiState.value.negativeWarning)
        assertNull(vm.uiState.value.voidingId)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun aSecondConfirmWhileTheFirstIsRunningEmitsASingleEvent() {
        show(f.expenseMovement("m1", f.bbva, "p1" to 300))
        val vm = viewModel()
        vm.onAction(MovementsUiAction.AskVoid(MovementId("m1")))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        f.movements.gate = gate

        vm.onAction(MovementsUiAction.ConfirmVoid("duplicado"))
        vm.onAction(MovementsUiAction.ConfirmVoid("duplicado"))
        gate.complete(Unit)

        assertEquals(1, f.store.events.size)
    }

    @Test
    fun askingOrDismissingWhileAVoidIsRunningDoesNotLoseItsResult() {
        show(f.expenseMovement("m1", f.bbva, "p1" to 300))
        val vm = viewModel()
        vm.onAction(MovementsUiAction.AskVoid(MovementId("m1")))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        f.movements.gate = gate
        vm.onAction(MovementsUiAction.ConfirmVoid("duplicado"))

        vm.onAction(MovementsUiAction.AskVoid(MovementId("otra")))
        vm.onAction(MovementsUiAction.DismissVoid)

        assertEquals(MovementId("m1"), vm.uiState.value.voidingId)
        gate.complete(Unit)
        assertEquals(MovementVoided(MovementId("m1"), "duplicado", null), f.store.events.single().event)
        assertNull(vm.uiState.value.voidingId)
    }
}
