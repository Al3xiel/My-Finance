package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.RegisterIncomeWithTopUpUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IncomeEditorTopUpTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val fare = FundId("fare")

    private fun viewModel(id: String? = null) = IncomeEditorViewModel(
        id, f.movements, f.accounts, f.sources, funds,
        RegisterIncomeUseCase(f.rules, f.recorder, f.ids),
        RegisterIncomeWithTopUpUseCase(f.rules, funds, f.recorder, f.ids, f.writeLock),
        CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock),
        f.clock,
    )

    @Test
    fun offersOnlyActiveFundsAndNoneWhenCorrecting() {
        funds.put(fare, "Pasajes")
        funds.put(FundId("old"), "Viejo", status = FundStatus.CLOSED)
        f.incomeMovement("inc", f.bbva, 1_000)

        assertEquals(listOf("Pasajes"), viewModel().uiState.value.fundOptions.map { it.label })
        assertTrue(viewModel("inc").uiState.value.fundOptions.isEmpty())
    }

    @Test
    fun choosingAFundProposesTheWholeIncomeAndClearingItDropsTheTopUp() {
        funds.put(fare, "Pasajes")
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.EditAmount("50"))

        vm.onAction(IncomeEditorUiAction.SelectTopUpFund(fare))
        assertEquals(fare, vm.uiState.value.topUpFundId)
        assertEquals("50", vm.uiState.value.topUpText)

        vm.onAction(IncomeEditorUiAction.SelectTopUpFund(null))
        assertEquals(null, vm.uiState.value.topUpFundId)
        assertEquals("", vm.uiState.value.topUpText)
    }

    @Test
    fun savesTheIncomeAndItsLinkedTopUp() {
        funds.put(fare, "Pasajes")
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.EditAmount("50"))
        vm.onAction(IncomeEditorUiAction.SelectTopUpFund(fare))
        vm.onAction(IncomeEditorUiAction.EditTopUp("30"))
        vm.onAction(IncomeEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(
            listOf(
                IncomeRegistered(MovementId("id-1"), f.bbva, f.pen(5_000), f.source, null, null),
                FundToppedUp(fare, f.pen(3_000), MovementId("id-1")),
            ),
            f.store.events.map { it.event },
        )
    }

    @Test
    fun aTopUpAboveTheIncomeShowsTheErrorAndSavesNothing() {
        funds.put(fare, "Pasajes")
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.EditAmount("50"))
        vm.onAction(IncomeEditorUiAction.SelectTopUpFund(fare))
        vm.onAction(IncomeEditorUiAction.EditTopUp("50.01"))
        vm.onAction(IncomeEditorUiAction.Save)

        assertEquals(BusinessError.ExceedsIncome, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun withoutAFundTheIncomeIsRegisteredAlone() {
        funds.put(fare, "Pasajes")
        val vm = viewModel()
        vm.onAction(IncomeEditorUiAction.EditAmount("50"))
        vm.onAction(IncomeEditorUiAction.Save)

        assertEquals(1, f.store.events.size)
        assertTrue(vm.uiState.value.saved)
    }
}
