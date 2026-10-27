package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExpenseEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    private fun viewModel(id: String? = null) = ExpenseEditorViewModel(
        id, f.movements, f.accounts, f.categories, f.tags,
        RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids),
        CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock),
        f.clock,
    )

    @Test
    fun proposesTheAccountAndCategoryOfTheLastExpense() {
        f.expenseMovement("old", f.cash, "p0" to 100).let {
            f.movements.movements[it.id] = it.copy(parts = it.parts.map { part -> part.copy(categoryId = f.fees) })
        }

        val state = viewModel().uiState.value

        assertEquals(f.cash, state.accountId)
        assertEquals(listOf(f.fees), state.parts.map { it.categoryId })
        assertEquals(f.today, state.occurredOn)
        assertFalse(state.isCorrection)
        assertTrue(state.loaded)
    }

    @Test
    fun withoutPreviousExpensesItProposesTheFirstAccountAndCategory() {
        val state = viewModel().uiState.value
        assertEquals(f.bbva, state.accountId)
        assertEquals(1, state.parts.size)
        assertEquals(state.categoryOptions.first().id, state.parts.single().categoryId?.value)
    }

    @Test
    fun aSinglePartTakesTheTotalAndSavesWithoutRetypingIt() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.EditTotal("3.50"))
        vm.onAction(ExpenseEditorUiAction.SelectPartCategory(vm.uiState.value.parts.single().key, f.food))
        vm.onAction(ExpenseEditorUiAction.EditNote("café"))
        vm.onAction(ExpenseEditorUiAction.Save)

        val event = f.store.events.single().event as ExpenseRegistered
        assertEquals(f.pen(350), event.total)
        assertEquals(listOf(f.pen(350)), event.parts.map { it.amount })
        assertEquals("café", event.note)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun multiplePartsShowWhatIsStillUnassignedAndRefuseToSaveUntilTheySum() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.EditTotal("10"))
        vm.onAction(ExpenseEditorUiAction.AddPart)
        val (first, second) = vm.uiState.value.parts.map { it.key }

        assertEquals("10", vm.uiState.value.parts.first().amountText)
        assertEquals(0L, vm.uiState.value.unassignedMinor)
        vm.onAction(ExpenseEditorUiAction.EditPartAmount(first, "6"))
        assertEquals(400L, vm.uiState.value.unassignedMinor)

        vm.onAction(ExpenseEditorUiAction.SelectPartCategory(second, f.food))
        vm.onAction(ExpenseEditorUiAction.EditPartAmount(second, "3"))
        vm.onAction(ExpenseEditorUiAction.Save)
        assertEquals(BusinessError.PartsMismatch, vm.uiState.value.error)
        assertEquals(0, f.store.events.size)

        vm.onAction(ExpenseEditorUiAction.EditPartAmount(second, "4"))
        assertEquals(0L, vm.uiState.value.unassignedMinor)
        assertNull(vm.uiState.value.error)
        vm.onAction(ExpenseEditorUiAction.Save)
        assertEquals(2, (f.store.events.single().event as ExpenseRegistered).parts.size)
    }

    @Test
    fun partsCanBeRemovedDownToOne() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.AddPart)
        val keys = vm.uiState.value.parts.map { it.key }
        vm.onAction(ExpenseEditorUiAction.RemovePart(keys.last()))
        vm.onAction(ExpenseEditorUiAction.RemovePart(keys.first()))
        assertEquals(1, vm.uiState.value.parts.size)
    }

    @Test
    fun partTagsToggleOnAndOff() {
        val vm = viewModel()
        val key = vm.uiState.value.parts.single().key
        vm.onAction(ExpenseEditorUiAction.TogglePartTag(key, f.tag))
        assertEquals(setOf(f.tag), vm.uiState.value.parts.single().tagIds)
        vm.onAction(ExpenseEditorUiAction.TogglePartTag(key, f.tag))
        assertTrue(vm.uiState.value.parts.single().tagIds.isEmpty())
    }

    @Test
    fun anInvalidTotalIsReportedWithoutEmitting() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.Save)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)

        vm.onAction(ExpenseEditorUiAction.EditTotal("5"))
        assertNull(vm.uiState.value.error)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun warnsBeforeSavingAnExpenseThatLeavesTheBalanceNegativeAndSavesWhenConfirmed() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.SelectAccount(f.bbva))
        vm.onAction(ExpenseEditorUiAction.EditTotal("15"))
        vm.onAction(ExpenseEditorUiAction.Save)

        assertEquals(BusinessError.WouldGoNegative(-500), vm.uiState.value.negativeWarning)
        assertEquals(0, f.store.events.size)

        vm.onAction(ExpenseEditorUiAction.ConfirmNegativeBalance)
        assertNull(vm.uiState.value.negativeWarning)
        assertEquals(1, f.store.events.size)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun dismissingTheWarningKeepsTheFormAndSavesNothing() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.EditTotal("15"))
        vm.onAction(ExpenseEditorUiAction.Save)
        vm.onAction(ExpenseEditorUiAction.DismissWarning)
        assertNull(vm.uiState.value.negativeWarning)
        assertFalse(vm.uiState.value.saved)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun correctionLoadsTheMovementAndAsksForAReasonBeforeEmitting() {
        f.expenseMovement("m1", f.bbva, "p1" to 400, "p2" to 100)
        val vm = viewModel("m1")

        val state = vm.uiState.value
        assertTrue(state.isCorrection)
        assertEquals("5", state.totalText)
        assertEquals(listOf("4", "1"), state.parts.map { it.amountText })

        vm.onAction(ExpenseEditorUiAction.EditTotal("4"))
        vm.onAction(ExpenseEditorUiAction.EditPartAmount(state.parts[0].key, "3"))
        vm.onAction(ExpenseEditorUiAction.Save)
        assertTrue(vm.uiState.value.showReasonDialog)
        assertEquals(0, f.store.events.size)

        vm.onAction(ExpenseEditorUiAction.ConfirmReason("monto mal"))

        val event = f.store.events.single().event as MovementCorrected
        assertEquals(MovementId("m1"), event.movementId)
        assertEquals("monto mal", event.reason)
        val snapshot = event.snapshot as MovementSnapshot.Expense
        assertEquals(listOf(PartId("p1"), PartId("p2")), snapshot.parts.map { it.partId })
        assertEquals(listOf(f.pen(300), f.pen(100)), snapshot.parts.map { it.amount })
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun correctionOfAMissingOrNonExpenseMovementIsMarkedNotFound() {
        f.incomeMovement("inc", f.bbva, 100)
        assertTrue(viewModel("ghost").uiState.value.notFound)
        assertTrue(viewModel("inc").uiState.value.notFound)
    }

    @Test
    fun categoriesOfASubcategoryAreLabelledWithTheirParent() {
        f.categories.categories[CategoryId("sub")] =
            com.al3xiel.my_finances.feature.categories.domain.model.Category(CategoryId("sub"), "Restaurantes", f.food, false)
        val labels = viewModel().uiState.value.categoryOptions.map { it.label }
        assertTrue("Comida › Restaurantes" in labels)
    }

    @Test
    fun dismissingTheWarningOfACorrectionAsksForTheReasonAgain() {
        f.expenseMovement("m1", f.bbva, "p1" to 400)
        val vm = viewModel("m1")
        vm.onAction(ExpenseEditorUiAction.EditTotal("15"))
        vm.onAction(ExpenseEditorUiAction.Save)
        vm.onAction(ExpenseEditorUiAction.ConfirmReason("monto mal"))
        assertEquals(BusinessError.WouldGoNegative(-100), vm.uiState.value.negativeWarning)

        vm.onAction(ExpenseEditorUiAction.DismissWarning)
        vm.onAction(ExpenseEditorUiAction.Save)

        assertTrue(vm.uiState.value.showReasonDialog)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun anUnexpectedFailureShowsAnErrorAndSavingAgainWorks() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.EditTotal("3"))
        f.accounts.failure = IllegalStateException("db")

        vm.onAction(ExpenseEditorUiAction.Save)
        assertEquals(BusinessError.Unexpected, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)

        f.accounts.failure = null
        vm.onAction(ExpenseEditorUiAction.Save)
        assertTrue(vm.uiState.value.saved)
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun aSecondSaveWhileTheFirstIsStillRunningDoesNotRegisterTwice() {
        val vm = viewModel()
        vm.onAction(ExpenseEditorUiAction.EditTotal("3"))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        f.accounts.gate = gate

        vm.onAction(ExpenseEditorUiAction.Save)
        vm.onAction(ExpenseEditorUiAction.Save)
        gate.complete(Unit)

        assertEquals(1, f.store.events.size)
    }
}
