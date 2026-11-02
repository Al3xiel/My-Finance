package com.al3xiel.my_finances.feature.reconciliation.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.model.event.ReconciliationResolved
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.ResolveReconciliationUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ReconciliationResolveViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()
    private val r1 = ReconciliationId("r1")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun open(difference: Long, status: ReconciliationStatus = ReconciliationStatus.OPEN) {
        reconciliations.reconciliations[r1] = Reconciliation(
            r1, f.bbva, pen(0), pen(0), status, null, null, null, Instant.EPOCH, null, false,
        )
        reconciliations.differences[r1] = pen(difference)
    }

    private fun viewModel(id: String = "r1") =
        ReconciliationResolveViewModel(id, reconciliations, ResolveReconciliationUseCase(reconciliations, f.recorder, f.ids))

    @Test
    fun aDifferenceOffersOnlyUnknownOrSuspectedAppErrorAndPreviewsTheUnidentifiedIncome() {
        open(240)

        val state = viewModel().uiState.value

        assertEquals(listOf(ReconciliationCause.SUSPECTED_APP_ERROR, ReconciliationCause.UNKNOWN).sortedBy { it.ordinal }, state.causes)
        assertEquals(ReconciliationCause.UNKNOWN, state.selectedCause)
        assertEquals(AdjustmentDirection.UNIDENTIFIED_INCOME, state.adjustment?.direction)
        assertEquals(pen(240), state.adjustment?.amount)
    }

    @Test
    fun aNegativeDifferencePreviewsAnUnidentifiedExpenseByItsAbsoluteValue() {
        open(-1_500)

        val adjustment = viewModel().uiState.value.adjustment

        assertEquals(AdjustmentDirection.UNIDENTIFIED_EXPENSE, adjustment?.direction)
        assertEquals(pen(1_500), adjustment?.amount)
    }

    @Test
    fun noDifferenceOffersTheSettledCausesAndNoAdjustment() {
        open(0)

        val state = viewModel().uiState.value

        assertEquals(
            listOf(ReconciliationCause.BALANCED, ReconciliationCause.MISSING_RECORD, ReconciliationCause.RECORDING_ERROR),
            state.causes,
        )
        assertEquals(ReconciliationCause.BALANCED, state.selectedCause)
        assertNull(state.adjustment)
    }

    @Test
    fun confirmingRecordsTheChosenCauseAndNote() {
        open(240)
        val vm = viewModel()
        vm.onAction(ReconciliationResolveUiAction.SelectCause(ReconciliationCause.UNKNOWN))
        vm.onAction(ReconciliationResolveUiAction.EditNote("no encontré el origen"))

        vm.onAction(ReconciliationResolveUiAction.Confirm)

        val event = f.store.events.single().event as ReconciliationResolved
        assertEquals(ReconciliationCause.UNKNOWN, event.cause)
        assertEquals("no encontré el origen", event.note)
        assertEquals(pen(240), event.adjustment?.amount)
        assertTrue(vm.uiState.value.resolved)
    }

    @Test
    fun aCauseThatTheDifferenceDoesNotAdmitIsIgnored() {
        open(240)
        val vm = viewModel()

        vm.onAction(ReconciliationResolveUiAction.SelectCause(ReconciliationCause.BALANCED))

        assertEquals(ReconciliationCause.UNKNOWN, vm.uiState.value.selectedCause)
    }

    @Test
    fun confirmingTwiceRecordsOnce() {
        open(0)
        val vm = viewModel()

        vm.onAction(ReconciliationResolveUiAction.Confirm)
        vm.onAction(ReconciliationResolveUiAction.Confirm)

        assertEquals(1, f.store.events.size)
    }

    @Test
    fun unknownOrClosedReconciliationsAreNotFound() {
        assertTrue(viewModel("zz").uiState.value.notFound)

        open(0, ReconciliationStatus.RESOLVED)
        val state = viewModel().uiState.value
        assertTrue(state.notFound)
        assertFalse(state.resolved)
        assertEquals(emptyList<ReconciliationCause>(), state.causes)
    }

    @Test
    fun aRejectionFromTheUseCaseShowsTheError() {
        open(240)
        val vm = viewModel()
        // Mientras el usuario estaba en la pantalla se canceló por otro lado.
        reconciliations.reconciliations[r1] = reconciliations.reconciliations.getValue(r1).copy(status = ReconciliationStatus.CANCELLED)

        vm.onAction(ReconciliationResolveUiAction.Confirm)

        assertEquals(BusinessError.ReconciliationNotOpen, vm.uiState.value.error)
        assertFalse(vm.uiState.value.resolved)
    }
}
