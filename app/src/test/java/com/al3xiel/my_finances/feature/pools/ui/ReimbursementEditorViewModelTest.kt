package com.al3xiel.my_finances.feature.pools.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.ReimbursementReceived
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import com.al3xiel.my_finances.feature.pools.domain.usecase.ReceiveReimbursementUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class ReimbursementEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val pools = FakePoolRepository()
    private val people = FakePersonRepository()
    private val mom = PersonId("mom")
    private val dogs = PoolId("dogs")
    private val cats = PoolId("cats")

    init {
        people.people[mom] = Person(mom, "Mamá", false)
        people.people[PersonId("old")] = Person(PersonId("old"), "Tío", true)
        line("p2", dogs, "Perros", day = 12, seq = 9, minor = 8_000)
        line("p1", dogs, "Perros", day = 3, seq = 4, minor = 12_000)
        line("c1", cats, "Gatos", day = 20, seq = 12, minor = 5_000)
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    /** Registra la parte como el repositorio de producción la devolvería: con nombres y como parte pendiente. */
    private fun line(id: String, pool: PoolId, poolName: String, day: Int, seq: Long, minor: Long) {
        val line = PendingPartLine(PartId(id), pool, poolName, LocalDate.of(2026, 10, day), seq, "Mascotas", emptyList(), pen(minor))
        pools.pendingLines += mom to line
        pools.pendingByPerson.getOrPut(mom) { mutableListOf() } += pool to line.toPendingPart()
    }

    private fun viewModel(personId: String? = mom.value, poolId: String? = null) = ReimbursementEditorViewModel(
        personId, poolId, people, f.accounts, pools,
        ReceiveReimbursementUseCase(pools, people, f.rules, f.recorder, f.ids, f.writeLock), f.clock,
    )

    private fun event() = f.store.events.single().event as ReimbursementReceived

    @Test
    fun listsThePendingPartsOldestFirstAndPicksTheFirstAccount() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals(mom, state.personId)
        assertEquals(listOf(PartId("p1"), PartId("p2"), PartId("c1")), state.lines.map { it.line.partId })
        assertEquals(25_000L, state.pendingTotal.amountMinor)
        assertEquals(AllocationStrategy.OLDEST_FIRST, state.strategy)
        assertEquals(f.bbva, state.accountId)
        assertEquals(listOf("BBVA", "Efectivo"), state.accountOptions.map { it.label })
    }

    @Test
    fun fromAPoolOnlyThatPoolsPartsAppear() {
        val state = viewModel(poolId = cats.value).uiState.value

        assertEquals(listOf(PartId("c1")), state.lines.map { it.line.partId })
        assertEquals(5_000L, state.pendingTotal.amountMinor)
    }

    @Test
    fun withoutAPersonThePickerOffersActivePeopleAndNothingIsListedUntilOneIsChosen() {
        val vm = viewModel(personId = null)
        assertEquals(listOf("Mamá"), vm.uiState.value.personOptions.map { it.label })
        assertTrue(vm.uiState.value.lines.isEmpty())

        vm.onAction(ReimbursementEditorUiAction.SelectPerson(mom))

        assertEquals(3, vm.uiState.value.lines.size)
    }

    @Test
    fun theOldestFirstPreviewShowsWhatEachPartWouldSettle() {
        val vm = viewModel()

        vm.onAction(ReimbursementEditorUiAction.EditAmount("150"))

        val state = vm.uiState.value
        assertEquals(listOf(12_000L, 3_000L, 0L), state.lines.map { it.willSettle.amountMinor })
        assertFalse(state.exceedsPending)
    }

    @Test
    fun anAmountAbovePendingIsFlaggedAndBlocksSaving() {
        val vm = viewModel()
        vm.onAction(ReimbursementEditorUiAction.EditAmount("251"))
        assertTrue(vm.uiState.value.exceedsPending)

        vm.onAction(ReimbursementEditorUiAction.Save)

        assertEquals(BusinessError.ExceedsPending, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun savingOldestFirstStoresTheComputedAllocations() {
        val vm = viewModel()
        vm.onAction(ReimbursementEditorUiAction.EditAmount("150"))
        vm.onAction(ReimbursementEditorUiAction.EditNote(" efectivo "))

        vm.onAction(ReimbursementEditorUiAction.Save)

        val saved = event()
        assertEquals(AllocationStrategy.OLDEST_FIRST, saved.strategy)
        assertEquals(listOf(PartId("p1") to 12_000L, PartId("p2") to 3_000L), saved.allocations.map { it.partId to it.amount.amountMinor })
        assertEquals(f.bbva, saved.accountId)
        assertEquals("efectivo", saved.note)
        assertTrue(vm.uiState.value.saved)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun manualAllocationsAreTypedPerPartAndMustAddUpToTheAmount() {
        val vm = viewModel()
        vm.onAction(ReimbursementEditorUiAction.SelectStrategy(AllocationStrategy.MANUAL))
        vm.onAction(ReimbursementEditorUiAction.EditAmount("100"))
        vm.onAction(ReimbursementEditorUiAction.EditManual(PartId("p2"), "60"))

        assertEquals(4_000L, vm.uiState.value.unassigned.amountMinor)
        vm.onAction(ReimbursementEditorUiAction.Save)
        assertEquals(BusinessError.PartsMismatch, vm.uiState.value.error)
        assertEquals(0, f.store.events.size)

        vm.onAction(ReimbursementEditorUiAction.EditManual(PartId("c1"), "40"))
        assertEquals(0L, vm.uiState.value.unassigned.amountMinor)
        vm.onAction(ReimbursementEditorUiAction.Save)

        val saved = event()
        assertEquals(AllocationStrategy.MANUAL, saved.strategy)
        assertEquals(listOf(PartId("p2") to 6_000L, PartId("c1") to 4_000L), saved.allocations.map { it.partId to it.amount.amountMinor }.sortedByDescending { it.second })
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun anEmptyOrInvalidAmountIsRejected() {
        val vm = viewModel()

        vm.onAction(ReimbursementEditorUiAction.Save)

        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun editingAfterAnErrorClearsIt() {
        val vm = viewModel()
        vm.onAction(ReimbursementEditorUiAction.Save)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)

        vm.onAction(ReimbursementEditorUiAction.EditAmount("10"))

        assertNull(vm.uiState.value.error)
    }
}
