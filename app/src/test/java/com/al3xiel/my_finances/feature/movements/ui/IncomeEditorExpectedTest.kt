package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeConfirmed
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ConfirmExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.RegisterIncomeWithTopUpUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class IncomeEditorExpectedTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val expected = FakeExpectedIncomeRepository()

    private fun viewModel(movementId: String? = null, expectedId: String? = null) = IncomeEditorViewModel(
        movementId, expectedId, f.movements, f.accounts, f.sources, funds,
        RegisterIncomeUseCase(f.rules, f.recorder, f.ids),
        RegisterIncomeWithTopUpUseCase(f.rules, funds, f.recorder, f.ids, f.writeLock),
        CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock),
        expected,
        ConfirmExpectedIncomeUseCase(f.rules, expected, funds, f.recorder, f.ids, f.writeLock),
        f.clock,
        com.al3xiel.my_finances.feature.reconciliation.domain.FakeReconciliationRepository(),
    )

    private fun pending(id: String, repeats: Boolean = false, account: com.al3xiel.my_finances.domain.model.AccountId = f.bbva, source: IncomeSourceId = f.source) =
        expected.put(id, account, source, estimated = 200_000, repeats = repeats)

    @Test
    fun suggestsThePendingOnesOfTheSameAccountAndSource() {
        pending("e1")
        pending("e2", account = f.cash)
        pending("e3", source = IncomeSourceId("other"))
        expected.put("e4", f.bbva, f.source, status = ExpectedIncomeStatus.CONFIRMED)

        val state = viewModel().uiState.value

        assertEquals(listOf("e1"), state.suggestions.map { it.id.value })
        assertEquals("Sueldo", state.suggestions.single().sourceName)
        assertEquals(200_000L, state.suggestions.single().estimated.amountMinor)
    }

    @Test
    fun anOverdueOneIsSuggestedToo() {
        expected.put("e1", f.bbva, f.source, status = ExpectedIncomeStatus.OVERDUE)

        assertEquals(listOf("e1"), viewModel().uiState.value.suggestions.map { it.id.value })
    }

    @Test
    fun suggestionsFollowTheChosenAccountAndSource() {
        pending("e2", account = f.cash)
        val vm = viewModel()
        assertTrue(vm.uiState.value.suggestions.isEmpty())

        vm.onAction(IncomeEditorUiAction.SelectAccount(f.cash))

        assertEquals(listOf("e2"), vm.uiState.value.suggestions.map { it.id.value })
    }

    @Test
    fun noSuggestionsWhenCorrectingAnIncome() {
        pending("e1")
        f.incomeMovement("inc", f.bbva, 1_000)

        assertTrue(viewModel("inc").uiState.value.suggestions.isEmpty())
    }

    @Test
    fun oneTapChoosesItAndProposesItsAmountOnlyWhenThereIsNone() {
        pending("e1")
        val vm = viewModel()

        vm.onAction(IncomeEditorUiAction.SelectExpected(ExpectedIncomeId("e1")))

        assertEquals("2000", vm.uiState.value.amountText)
        assertEquals(ExpectedIncomeId("e1"), vm.uiState.value.selectedExpected?.id)
        assertTrue(vm.uiState.value.suggestions.isEmpty())

        val typed = viewModel()
        typed.onAction(IncomeEditorUiAction.EditAmount("1950"))
        typed.onAction(IncomeEditorUiAction.SelectExpected(ExpectedIncomeId("e1")))
        assertEquals("1950", typed.uiState.value.amountText)
    }

    @Test
    fun savingWithOneChosenConfirmsItInOneCorrelationAndSchedulesTheNext() {
        pending("e1", repeats = true)
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.SelectExpected(ExpectedIncomeId("e1")))
        vm.onAction(IncomeEditorUiAction.EditAmount("2050"))

        vm.onAction(IncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        val events = f.store.events.map { it.event }
        assertEquals(
            listOf("IncomeRegistered", "ExpectedIncomeConfirmed", "ExpectedIncomeScheduled"),
            events.map { it.type },
        )
        assertEquals(205_000L, (events[0] as IncomeRegistered).amount.amountMinor)
        assertEquals(ExpectedIncomeConfirmed(ExpectedIncomeId("e1"), MovementId("id-1"), ExpectedIncomeId("id-2")), events[1])
        assertEquals(LocalDate.of(2026, 11, 28), (events[2] as ExpectedIncomeScheduled).expectedFrom)
        assertEquals(1, f.store.events.map { it.correlationId }.toSet().size)
    }

    @Test
    fun clearingTheChoiceSavesAPlainIncome() {
        pending("e1")
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.SelectExpected(ExpectedIncomeId("e1")))
        vm.onAction(IncomeEditorUiAction.ClearExpected)
        assertNull(vm.uiState.value.selectedExpected)

        vm.onAction(IncomeEditorUiAction.Save)

        assertEquals(listOf("IncomeRegistered"), f.store.events.map { it.event.type })
    }

    @Test
    fun openedFromTheListItPrefillsAccountSourceAndAmount() {
        f.sources.sources[IncomeSourceId("bonus")] = IncomeSource(IncomeSourceId("bonus"), "Bono", IncomeSourceKind.REGULAR, null)
        expected.put("e1", f.cash, IncomeSourceId("bonus"), estimated = 80_000)

        val state = viewModel(expectedId = "e1").uiState.value

        assertTrue(state.loaded)
        assertEquals(f.cash, state.accountId)
        assertEquals(IncomeSourceId("bonus"), state.sourceId)
        assertEquals("800", state.amountText)
        assertEquals(ExpectedIncomeId("e1"), state.selectedExpected?.id)
    }

    @Test
    fun openedFromTheListItConfirmsOnSave() {
        pending("e1")
        val vm = viewModel(expectedId = "e1")

        vm.onAction(IncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(listOf("IncomeRegistered", "ExpectedIncomeConfirmed"), f.store.events.map { it.event.type })
    }

    @Test
    fun aMissingOrResolvedPresetIsNotFound() {
        expected.put("done", f.bbva, f.source, status = ExpectedIncomeStatus.CANCELLED)

        assertTrue(viewModel(expectedId = "done").uiState.value.notFound)
        assertTrue(viewModel(expectedId = "ghost").uiState.value.notFound)
    }

    @Test
    fun anExpectedWithAnArchivedAccountIsRejectedOnSave() {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        expected.put("e1", f.cash, f.source)
        val vm = viewModel(expectedId = "e1")
        vm.onAction(IncomeEditorUiAction.SelectAccount(f.bbva))

        vm.onAction(IncomeEditorUiAction.Save)

        assertEquals(BusinessError.AlreadyArchived, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)
        assertTrue(f.store.events.isEmpty())
    }
}
