package com.al3xiel.my_finances.feature.funds.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.UntrackedSpendData
import com.al3xiel.my_finances.feature.funds.domain.usecase.CountFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FundCountViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val fare = FundId("fare")

    private fun viewModel(id: String = "fare") = FundCountViewModel(
        id, funds, f.accounts, f.categories, f.clock, CountFundUseCase(funds, f.rules, f.recorder, f.ids, f.writeLock),
    )

    private fun fund(endsOn: LocalDate? = null) = funds.put(
        fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, 500, LocalDate.of(2026, 10, 1), endsOn,
        allocated = 5_000, detailed = 500, usageDays = 2,
    )

    @Test
    fun proposesTheFirstAccountAndCategoryAndShowsNothingUntilSomethingIsTyped() {
        fund()

        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals(f.bbva, state.accountId)
        assertEquals(f.food, state.categoryId)
        assertNull(state.untracked)
        assertNull(state.realSpent)
    }

    @Test
    fun whatYouDeclareShowsTheRealSpendTheEstimateAndTheUntrackedPart() {
        fund()
        val vm = viewModel()

        vm.onAction(FundCountUiAction.EditDeclared("38"))

        val state = vm.uiState.value
        // asignado 50,00 − declarado 38,00 = gastado real 12,00; estimado 2 días × 5,00 = 10,00; sin anotar 50 − 5 − 38 = 7,00
        assertEquals(1_200L, state.realSpent?.amountMinor)
        assertEquals(1_000L, state.estimatedSpent?.amountMinor)
        assertEquals(700L, state.untracked?.amountMinor)
        assertNull(state.rejection)
    }

    @Test
    fun declaringMoreThanWhatIsLeftIsRejectedInThePreview() {
        fund()
        val vm = viewModel()

        vm.onAction(FundCountUiAction.EditDeclared("46"))

        assertEquals(BusinessError.DeclaredAboveRemaining, vm.uiState.value.rejection)
        assertNull(vm.uiState.value.untracked)
    }

    @Test
    fun confirmingCountsTheFundWithTheChosenAccountAndCategory() {
        fund()
        val vm = viewModel()
        vm.onAction(FundCountUiAction.EditDeclared("38"))
        vm.onAction(FundCountUiAction.SelectAccount(f.cash))
        vm.onAction(FundCountUiAction.SelectCategory(f.fees))
        vm.onAction(FundCountUiAction.Confirm)

        assertTrue(vm.uiState.value.saved)
        assertEquals(
            FundCounted(fare, f.pen(3_800), UntrackedSpendData(MovementId("id-1"), f.cash, f.fees, f.pen(700))),
            f.store.events.single().event,
        )
    }

    @Test
    fun anEmptyOrRejectedCountShowsTheErrorAndSavesNothing() {
        fund()
        val vm = viewModel()

        vm.onAction(FundCountUiAction.Confirm)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)

        vm.onAction(FundCountUiAction.EditDeclared("46"))
        assertNull(vm.uiState.value.error)
        vm.onAction(FundCountUiAction.Confirm)
        assertEquals(BusinessError.DeclaredAboveRemaining, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun suggestsCountingBeforeClosingOnceTheFundHasEnded() {
        fund(endsOn = LocalDate.of(2026, 10, 25))
        assertTrue(viewModel().uiState.value.suggestBeforeClose)

        fund(endsOn = LocalDate.of(2026, 10, 31))
        assertFalse(viewModel().uiState.value.suggestBeforeClose)
    }

    @Test
    fun aMissingFundIsNotFound() {
        assertTrue(viewModel("ghost").uiState.value.notFound)
    }
}
