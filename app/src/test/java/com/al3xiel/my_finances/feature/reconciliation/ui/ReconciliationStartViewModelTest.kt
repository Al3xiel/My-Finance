package com.al3xiel.my_finances.feature.reconciliation.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.RecordBalanceCheckUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ReconciliationStartViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()

    private fun viewModel(account: String = "bbva") = ReconciliationStartViewModel(
        account, f.accounts, reconciliations, RecordBalanceCheckUseCase(f.accounts, reconciliations, f.recorder, f.ids),
    )

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test
    fun showsTheAccountAndWhatTheAppSays() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals("BBVA", state.accountName)
        assertEquals(pen(1_000), state.appBalance)
        assertNull(state.difference)
    }

    @Test
    fun anUnknownAccountIsNotFound() {
        assertTrue(viewModel("zz").uiState.value.notFound)
    }

    @Test
    fun typingTheBankBalanceShowsTheDifferenceIncludingZeroAndNegatives() {
        val vm = viewModel()

        vm.onAction(ReconciliationStartUiAction.EditReal("12.40"))
        assertEquals(pen(240), vm.uiState.value.difference)

        vm.onAction(ReconciliationStartUiAction.EditReal("0"))
        assertEquals(pen(-1_000), vm.uiState.value.difference)

        vm.onAction(ReconciliationStartUiAction.EditReal("-5"))
        assertEquals(pen(-1_500), vm.uiState.value.difference)
        assertEquals("-5", vm.uiState.value.realText)
    }

    @Test
    fun confirmingRecordsTheCheckWithoutTouchingBalancesAndGoesToTheOpenOne() {
        val vm = viewModel()
        vm.onAction(ReconciliationStartUiAction.EditReal("12.40"))

        vm.onAction(ReconciliationStartUiAction.Confirm)

        val event = f.store.events.single().event as BalanceCheckRecorded
        assertEquals(pen(1_240), event.realBalance)
        assertEquals(pen(1_000), event.computedBalanceSnapshot)
        assertEquals(event.reconciliationId, vm.uiState.value.recordedId)
    }

    @Test
    fun aNegativeRealBalanceIsRecordedAsIs() {
        val vm = viewModel()
        vm.onAction(ReconciliationStartUiAction.EditReal("-5"))

        vm.onAction(ReconciliationStartUiAction.Confirm)

        assertEquals(pen(-500), (f.store.events.single().event as BalanceCheckRecorded).realBalance)
    }

    @Test
    fun confirmingWithoutAnAmountShowsAnErrorAndRecordsNothing() {
        val vm = viewModel()

        vm.onAction(ReconciliationStartUiAction.Confirm)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)

        vm.onAction(ReconciliationStartUiAction.EditReal("-"))
        vm.onAction(ReconciliationStartUiAction.Confirm)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun editingClearsTheError() {
        val vm = viewModel()
        vm.onAction(ReconciliationStartUiAction.Confirm)

        vm.onAction(ReconciliationStartUiAction.EditReal("1"))

        assertNull(vm.uiState.value.error)
    }

    @Test
    fun ifTheAccountAlreadyHasAnOpenReconciliationItJumpsToIt() {
        val open = ReconciliationId("r9")
        reconciliations.reconciliations[open] = Reconciliation(
            open, f.bbva, pen(0), pen(0), ReconciliationStatus.OPEN, null, null, null, Instant.EPOCH, null, false,
        )

        val state = viewModel().uiState.value

        assertEquals(open, state.openReconciliationId)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun aRejectedRecordingKeepsTheFormAndShowsTheError() {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        val vm = viewModel("cash")
        vm.onAction(ReconciliationStartUiAction.EditReal("5"))

        vm.onAction(ReconciliationStartUiAction.Confirm)

        assertEquals(BusinessError.AlreadyArchived, vm.uiState.value.error)
        assertNull(vm.uiState.value.recordedId)
        assertNotNull(vm.uiState.value.accountName)
    }
}
