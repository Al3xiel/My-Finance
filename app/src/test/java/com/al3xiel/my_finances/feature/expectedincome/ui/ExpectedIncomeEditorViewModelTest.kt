package com.al3xiel.my_finances.feature.expectedincome.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeEdited
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.EditExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ScheduleExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ExpectedIncomeEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val repo = FakeExpectedIncomeRepository()

    private fun viewModel(id: String? = null) = ExpectedIncomeEditorViewModel(
        id, repo, f.accounts, f.sources,
        ScheduleExpectedIncomeUseCase(f.rules, f.recorder, f.ids),
        EditExpectedIncomeUseCase(f.rules, repo, f.recorder, f.writeLock),
        f.clock,
    )

    @Test
    fun aNewOneStartsTodayWithAWeekAheadAndTheFirstAccountAndSource() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertFalse(state.isEditing)
        assertEquals(f.today, state.expectedFrom)
        assertEquals(f.today.plusDays(7), state.expectedTo)
        assertEquals(f.source, state.sourceId)
        assertEquals(listOf("BBVA", "Efectivo"), state.accountOptions.map { it.label }.sorted())
    }

    @Test
    fun schedulesWithTheEnteredValues() {
        val vm = viewModel()
        vm.onAction(ExpectedIncomeEditorUiAction.SelectAccount(f.bbva))
        vm.onAction(ExpectedIncomeEditorUiAction.EditAmount("2000"))
        vm.onAction(ExpectedIncomeEditorUiAction.SetFrom(LocalDate.of(2026, 10, 28)))
        vm.onAction(ExpectedIncomeEditorUiAction.SetTo(LocalDate.of(2026, 11, 5)))
        vm.onAction(ExpectedIncomeEditorUiAction.SetRepeatsMonthly(true))

        vm.onAction(ExpectedIncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(
            listOf(ExpectedIncomeScheduled(ExpectedIncomeId("id-1"), f.source, f.bbva, f.pen(200_000), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 5), true)),
            f.store.events.map { it.event },
        )
    }

    @Test
    fun anEmptyAmountIsRejectedAsInvalid() {
        val vm = viewModel()

        vm.onAction(ExpectedIncomeEditorUiAction.Save)

        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun anInvertedRangeIsRejectedAndEditingClearsTheError() {
        val vm = viewModel()
        vm.onAction(ExpectedIncomeEditorUiAction.EditAmount("100"))
        vm.onAction(ExpectedIncomeEditorUiAction.SetFrom(f.today.plusDays(10)))

        vm.onAction(ExpectedIncomeEditorUiAction.Save)
        assertEquals(BusinessError.InvalidRange, vm.uiState.value.error)

        vm.onAction(ExpectedIncomeEditorUiAction.SetFrom(f.today))
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun aPastRangeCanBeScheduled() {
        val vm = viewModel()
        vm.onAction(ExpectedIncomeEditorUiAction.EditAmount("100"))
        vm.onAction(ExpectedIncomeEditorUiAction.SetFrom(LocalDate.of(2026, 1, 1)))
        vm.onAction(ExpectedIncomeEditorUiAction.SetTo(LocalDate.of(2026, 1, 5)))

        vm.onAction(ExpectedIncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun editingLoadsThePendingOneAndSendsOnlyWhatChanged() {
        repo.put("e1", f.bbva, f.source, estimated = 200_000)
        val vm = viewModel("e1")
        assertTrue(vm.uiState.value.isEditing)
        assertEquals("2000", vm.uiState.value.amountText)

        vm.onAction(ExpectedIncomeEditorUiAction.EditAmount("2100"))
        vm.onAction(ExpectedIncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(
            listOf(ExpectedIncomeEdited(ExpectedIncomeId("e1"), null, null, f.pen(210_000), null, null, null)),
            f.store.events.map { it.event },
        )
    }

    @Test
    fun anOverdueOneCanBeEdited() {
        repo.put("e1", f.bbva, f.source, status = ExpectedIncomeStatus.OVERDUE)

        assertFalse(viewModel("e1").uiState.value.notEditable)
    }

    @Test
    fun aResolvedOrMissingOneCannotBeEdited() {
        repo.put("done", f.bbva, f.source, status = ExpectedIncomeStatus.CONFIRMED)

        assertTrue(viewModel("done").uiState.value.notEditable)
        assertTrue(viewModel("ghost").uiState.value.notFound)
    }

    @Test
    fun anArchivedCurrentAccountStaysVisibleSoItCanBeChanged() {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        repo.put("e1", f.cash, f.source)

        val options = viewModel("e1").uiState.value.accountOptions.map { it.id }

        assertTrue(f.cash.value in options)
        assertTrue(f.bbva.value in options)
    }
}
