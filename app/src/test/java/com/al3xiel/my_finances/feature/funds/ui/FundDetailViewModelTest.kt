package com.al3xiel.my_finances.feature.funds.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.FundClosed
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.domain.model.event.UsageDayMarked
import com.al3xiel.my_finances.domain.model.event.UsageDayUnmarked
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.funds.domain.usecase.CloseFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.funds.domain.usecase.MarkUsageDayUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.TopUpFundUseCase
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class FundDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC)
    private val recorder = EventRecorder(store, SequentialIdGenerator(), clock)
    private val funds = FakeFundRepository()
    private val fare = FundId("fare")
    private val food = FundId("food")
    private val today = LocalDate.of(2026, 10, 27)

    private fun viewModel(id: String = "fare") = FundDetailViewModel(
        id, funds, clock,
        TopUpFundUseCase(funds, recorder, MovementWriteLock()),
        MarkUsageDayUseCase(funds, recorder, MovementWriteLock()),
        CloseFundUseCase(funds, recorder, MovementWriteLock()),
    )

    private fun existing() {
        funds.put(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, 500, LocalDate.of(2026, 10, 1), null, allocated = 5_000)
        funds.put(food, "Comida")
    }

    @Test
    fun showsTheFundItsDaysAndTheOtherActiveFundsAsRolloverTargets() {
        existing()
        funds.usage[fare] = setOf(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3))

        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals("Pasajes", state.overview?.fund?.name)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5)), state.usageDays)
        assertEquals(listOf(food), state.rollOverTargets.map { it.fund.id })
        assertEquals(today, state.today)
    }

    @Test
    fun anUnknownFundIsNotFound() {
        val state = viewModel("ghost").uiState.value

        assertTrue(state.notFound)
    }

    @Test
    fun topsUpWithTheTypedAmount() {
        existing()
        val vm = viewModel()

        vm.onAction(FundDetailUiAction.TopUp("20.50"))

        assertEquals(FundToppedUp(fare, Money(2_050, CurrencyCode.PEN), null), store.events.single().event)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun anUnreadableAmountOrOneAboveTheAvailableShowsANotice() {
        existing()
        funds.available = 1_000
        val vm = viewModel()

        vm.onAction(FundDetailUiAction.TopUp("."))
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.notice)

        vm.onAction(FundDetailUiAction.DismissNotice)
        vm.onAction(FundDetailUiAction.TopUp("10.01"))
        assertEquals(BusinessError.ExceedsAvailable, vm.uiState.value.notice)
        assertTrue(store.events.isEmpty())

        vm.onAction(FundDetailUiAction.DismissNotice)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun togglingADayMarksItAndTogglingAMarkedDayUnmarksIt() {
        existing()
        funds.usage[fare] = setOf(LocalDate.of(2026, 10, 5))
        val vm = viewModel()

        vm.onAction(FundDetailUiAction.ToggleDay(LocalDate.of(2026, 10, 4)))
        vm.onAction(FundDetailUiAction.ToggleDay(LocalDate.of(2026, 10, 5)))

        assertEquals(
            listOf(UsageDayMarked(fare, LocalDate.of(2026, 10, 4)), UsageDayUnmarked(fare, LocalDate.of(2026, 10, 5))),
            store.events.map { it.event },
        )
    }

    @Test
    fun closesReleasingOrRollingOver() {
        existing()
        val vm = viewModel()

        vm.onAction(FundDetailUiAction.Close(RemainderPolicy.ROLL_OVER, food))

        assertEquals(FundClosed(fare, RemainderPolicy.ROLL_OVER, food, Money(5_000, CurrencyCode.PEN)), store.events.single().event)
        assertFalse(vm.uiState.value.notice != null)
    }

    @Test
    fun rollingOverWithoutATargetShowsANotice() {
        existing()
        val vm = viewModel()

        vm.onAction(FundDetailUiAction.Close(RemainderPolicy.ROLL_OVER, null))

        assertEquals(BusinessError.NotFound, vm.uiState.value.notice)
        assertTrue(store.events.isEmpty())
    }
}
