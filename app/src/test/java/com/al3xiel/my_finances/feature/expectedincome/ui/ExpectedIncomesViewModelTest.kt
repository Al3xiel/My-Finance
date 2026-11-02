package com.al3xiel.my_finances.feature.expectedincome.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.CancelExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ExpectedIncomesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val repo = FakeExpectedIncomeRepository()

    private fun viewModel() =
        ExpectedIncomesViewModel(repo, f.accounts, f.sources, CancelExpectedIncomeUseCase(repo, f.recorder, f.writeLock))

    private fun put(id: String, status: ExpectedIncomeStatus, from: Int = 28, account: com.al3xiel.my_finances.domain.model.AccountId = f.bbva) =
        repo.put(id, account, f.source, from = LocalDate.of(2026, 10, from), to = LocalDate.of(2026, 11, 5), status = status)

    @Test
    fun listsOverdueFirstThenScheduledThenWhatIsAlreadyResolved() {
        put("cancelled", ExpectedIncomeStatus.CANCELLED)
        put("confirmed", ExpectedIncomeStatus.CONFIRMED)
        put("scheduled", ExpectedIncomeStatus.SCHEDULED)
        put("overdue-late", ExpectedIncomeStatus.OVERDUE, from = 20)
        put("overdue-early", ExpectedIncomeStatus.OVERDUE, from = 10)

        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals(
            listOf("overdue-early", "overdue-late", "scheduled", "confirmed", "cancelled"),
            state.items.map { it.income.id.value },
        )
    }

    @Test
    fun carriesTheNamesOfTheAccountAndTheSource() {
        put("e1", ExpectedIncomeStatus.SCHEDULED)

        val item = viewModel().uiState.value.items.single()

        assertEquals("BBVA", item.accountName)
        assertEquals("Sueldo", item.sourceName)
        assertFalse(item.accountArchived)
        assertTrue(item.canConfirm)
    }

    @Test
    fun anArchivedAccountIsMarkedAndBlocksConfirmingButNotEditingOrCancelling() {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        put("e1", ExpectedIncomeStatus.OVERDUE, account = f.cash)

        val item = viewModel().uiState.value.items.single()

        assertTrue(item.accountArchived)
        assertFalse(item.canConfirm)
        assertTrue(item.isPending)
    }

    @Test
    fun resolvedOnesOfferNoActions() {
        put("confirmed", ExpectedIncomeStatus.CONFIRMED)
        put("cancelled", ExpectedIncomeStatus.CANCELLED)

        viewModel().uiState.value.items.forEach {
            assertFalse(it.isPending)
            assertFalse(it.canConfirm)
        }
    }

    @Test
    fun cancellingAsksFirstThenRecordsTheTrimmedReason() {
        put("e1", ExpectedIncomeStatus.SCHEDULED)
        val vm = viewModel()

        vm.onAction(ExpectedIncomesUiAction.AskCancel(ExpectedIncomeId("e1")))
        assertEquals(ExpectedIncomeId("e1"), vm.uiState.value.cancelling)
        assertTrue(f.store.events.isEmpty())

        vm.onAction(ExpectedIncomesUiAction.ConfirmCancel("  ya no cobro ahí "))

        assertNull(vm.uiState.value.cancelling)
        assertNull(vm.uiState.value.error)
        assertEquals(listOf(ExpectedIncomeCancelled(ExpectedIncomeId("e1"), "ya no cobro ahí")), f.store.events.map { it.event })
    }

    @Test
    fun dismissingTheDialogCancelsNothing() {
        put("e1", ExpectedIncomeStatus.SCHEDULED)
        val vm = viewModel()
        vm.onAction(ExpectedIncomesUiAction.AskCancel(ExpectedIncomeId("e1")))

        vm.onAction(ExpectedIncomesUiAction.DismissCancel)

        assertNull(vm.uiState.value.cancelling)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun aRejectedCancelShowsTheBusinessError() {
        put("e1", ExpectedIncomeStatus.CONFIRMED)
        val vm = viewModel()
        vm.onAction(ExpectedIncomesUiAction.AskCancel(ExpectedIncomeId("e1")))

        vm.onAction(ExpectedIncomesUiAction.ConfirmCancel(null))

        assertEquals(BusinessError.NotPending, vm.uiState.value.error)
        vm.onAction(ExpectedIncomesUiAction.DismissError)
        assertNull(vm.uiState.value.error)
    }
}
