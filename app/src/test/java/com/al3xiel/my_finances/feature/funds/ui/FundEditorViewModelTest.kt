package com.al3xiel.my_finances.feature.funds.ui

import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.FundCreated
import com.al3xiel.my_finances.domain.model.event.FundEdited
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.funds.domain.model.FundEditFacts
import com.al3xiel.my_finances.feature.funds.domain.usecase.CreateFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.EditFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
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

class FundEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC)
    private val recorder = EventRecorder(store, ids, clock)
    private val funds = FakeFundRepository()
    private val fare = FundId("fare")
    private val today = LocalDate.of(2026, 10, 27)

    private fun viewModel(id: String? = null) =
        FundEditorViewModel(id, funds, clock, CreateFundUseCase(funds, recorder, ids), EditFundUseCase(funds, recorder, MovementWriteLock()))

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test
    fun aNewFundStartsTodayInDetailedModeWithNothingLocked() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertFalse(state.isEditing)
        assertEquals(today, state.startsOn)
        assertEquals(ConsumptionMode.DETAILED, state.mode)
        assertFalse(state.modeLocked)
        assertFalse(state.startLocked)
    }

    @Test
    fun savesANewEstimatedFund() {
        val vm = viewModel()
        vm.onAction(FundEditorUiAction.EditName("Pasajes"))
        vm.onAction(FundEditorUiAction.SelectMode(ConsumptionMode.DAILY_ESTIMATE))
        vm.onAction(FundEditorUiAction.EditEstimate("5"))
        vm.onAction(FundEditorUiAction.SetEndsOn(LocalDate.of(2026, 10, 31)))
        vm.onAction(FundEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(
            FundCreated(FundId("id-1"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), today, LocalDate.of(2026, 10, 31)),
            store.events.single().event,
        )
    }

    @Test
    fun anEstimatedFundWithoutAnEstimateShowsTheError() {
        val vm = viewModel()
        vm.onAction(FundEditorUiAction.EditName("Pasajes"))
        vm.onAction(FundEditorUiAction.SelectMode(ConsumptionMode.DAILY_ESTIMATE))
        vm.onAction(FundEditorUiAction.Save)

        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)

        vm.onAction(FundEditorUiAction.EditEstimate("5"))
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun editingLoadsTheFundAndLocksWhatHasActivity() {
        funds.put(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, 500, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        funds.facts[fare] = FundEditFacts(hasUsageDays = true, hasCounts = false, hasAssignedExpenses = true)

        val state = viewModel("fare").uiState.value

        assertTrue(state.isEditing)
        assertEquals("Pasajes", state.name)
        assertEquals("5", state.estimateText)
        assertEquals(LocalDate.of(2026, 10, 31), state.endsOn)
        assertTrue(state.modeLocked)
        assertTrue(state.startLocked)
    }

    @Test
    fun savesTheEditsOfAnExistingFund() {
        funds.put(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, 500, LocalDate.of(2026, 10, 1))
        val vm = viewModel("fare")

        vm.onAction(FundEditorUiAction.EditName("Transporte"))
        vm.onAction(FundEditorUiAction.EditEstimate("6"))
        vm.onAction(FundEditorUiAction.Save)

        assertTrue(vm.uiState.value.saved)
        assertEquals(
            FundEdited(fare, "Transporte", ConsumptionMode.DAILY_ESTIMATE, pen(600), LocalDate.of(2026, 10, 1), null),
            store.events.single().event,
        )
    }

    @Test
    fun aMissingFundIsNotFound() {
        assertTrue(viewModel("ghost").uiState.value.notFound)
    }
}
