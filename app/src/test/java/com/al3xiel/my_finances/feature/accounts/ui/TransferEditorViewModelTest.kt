package com.al3xiel.my_finances.feature.accounts.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransferEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()

    init {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo").copy(type = AccountType.CASH)
        f.categories.categories[SystemCatalog.feeCategoryId] = Category(SystemCatalog.feeCategoryId, "Comisiones", null, false)
    }

    private fun viewModel() = TransferEditorViewModel(
        f.accounts,
        RegisterTransferUseCase(f.rules, f.accounts, f.recorder, f.ids),
        f.clock,
    )

    @Test
    fun proposesTwoDifferentAccountsAndLabelsABankToCashTransferAsAWithdrawal() {
        val state = viewModel().uiState.value

        assertEquals(f.bbva, state.fromId)
        assertEquals(f.cash, state.toId)
        assertEquals(TransferLabel.WITHDRAWAL, state.label)
        assertEquals(f.today, state.occurredOn)
    }

    @Test
    fun theLabelFollowsTheAccountTypes() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.SelectFrom(f.cash))
        vm.onAction(TransferEditorUiAction.SelectTo(f.bbva))
        assertEquals(TransferLabel.DEPOSIT, vm.uiState.value.label)

        f.accounts.accounts[AccountId("bcp")] = f.account(AccountId("bcp"), "BCP")
        val other = viewModel()
        other.onAction(TransferEditorUiAction.SelectTo(AccountId("bcp")))
        assertEquals(TransferLabel.TRANSFER, other.uiState.value.label)
    }

    @Test
    fun savesATransferWithAnOptionalFeeInTheFeesCategory() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.EditAmount("3"))
        vm.onAction(TransferEditorUiAction.EditFee("0.5"))
        vm.onAction(TransferEditorUiAction.EditNote("retiro"))
        vm.onAction(TransferEditorUiAction.Save)

        val event = f.store.events.single().event as TransferRegistered
        assertEquals(f.pen(300), event.amount)
        assertEquals(FeeData(f.pen(50), SystemCatalog.feeCategoryId), event.fee)
        assertEquals("retiro", event.note)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun noFeeTextMeansNoFee() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.EditAmount("3"))
        vm.onAction(TransferEditorUiAction.Save)
        assertNull((f.store.events.single().event as TransferRegistered).fee)
    }

    @Test
    fun rejectsSameAccountAndInvalidAmountWithoutEmitting() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.Save)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)

        vm.onAction(TransferEditorUiAction.EditAmount("3"))
        vm.onAction(TransferEditorUiAction.SelectTo(f.bbva))
        vm.onAction(TransferEditorUiAction.Save)
        assertEquals(BusinessError.SameAccount, vm.uiState.value.error)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun warnsWhenTheAmountPlusFeeLeavesTheSourceNegative() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.EditAmount("9.70"))
        vm.onAction(TransferEditorUiAction.EditFee("0.50"))
        vm.onAction(TransferEditorUiAction.Save)

        assertEquals(BusinessError.WouldGoNegative(-20), vm.uiState.value.negativeWarning)
        assertFalse(vm.uiState.value.saved)

        vm.onAction(TransferEditorUiAction.ConfirmNegativeBalance)
        assertTrue(vm.uiState.value.saved)
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun anUnexpectedFailureShowsAnErrorAndSavingAgainWorks() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.EditAmount("3"))
        f.accounts.failure = IllegalStateException("db")

        vm.onAction(TransferEditorUiAction.Save)
        assertEquals(BusinessError.Unexpected, vm.uiState.value.error)

        f.accounts.failure = null
        vm.onAction(TransferEditorUiAction.Save)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun aSecondSaveWhileTheFirstIsStillRunningDoesNotRegisterTwice() {
        val vm = viewModel()
        vm.onAction(TransferEditorUiAction.EditAmount("3"))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        f.accounts.gate = gate

        vm.onAction(TransferEditorUiAction.Save)
        vm.onAction(TransferEditorUiAction.Save)
        gate.complete(Unit)

        assertEquals(1, f.store.events.size)
    }
}
