package com.al3xiel.my_finances.feature.pools.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.share.SharedFilesCleaner
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.SettlementStatementGenerated
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.model.StatementPart
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import com.al3xiel.my_finances.feature.pools.domain.usecase.GenerateSettlementStatementUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.PrepareSettlementStatementUseCase
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SettlementStatementViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @get:Rule val cache = TemporaryFolder()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val recorder = EventRecorder(store, ids, Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC))
    private val pools = FakePoolRepository()
    private val people = FakePersonRepository()

    private val dogs = PoolId("dogs")
    private val period = PoolPeriodId("dogs-p1")
    private val mom = PersonId("mom")

    init {
        people.people[mom] = Person(mom, "Mamá", false)
        pool(PoolKind.PERMANENT)
        pools.currentPeriods[dogs] = PoolPeriod(period, dogs, LocalDate.of(2026, 10, 1), null, PoolPeriodStatus.OPEN, null, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN))
        pools.periodParts[period] = listOf(
            StatementPart(PartId("p1"), LocalDate.of(2026, 10, 3), 4, "Mascotas", listOf("Perros"), Money(12_000, CurrencyCode.PEN), Money(12_000, CurrencyCode.PEN)),
        )
    }

    private fun pool(kind: PoolKind) {
        pools.pools[dogs] = Pool(dogs, "Perros – mamá", mom, kind, null, emptyList(), false)
    }

    private lateinit var shareDir: File

    private fun viewModel(): SettlementStatementViewModel {
        shareDir = cache.newFolder("share")
        return SettlementStatementViewModel(
            dogs.value, pools,
            PrepareSettlementStatementUseCase(pools, people, recorder),
            GenerateSettlementStatementUseCase(pools, people, recorder, ids, com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock()),
            SharedFilesCleaner(shareDir, Dispatchers.Unconfined),
            StatementSharer { null },
        )
    }

    @Test
    fun showsThePreviewWithoutRecordingAnything() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals(PoolKind.PERMANENT, state.kind)
        assertEquals("Perros – mamá", state.content!!.poolName)
        assertEquals(12_000L, state.content!!.pendingTotal.amountMinor)
        assertEquals(StatementFormat.PDF, state.format)
        assertFalse(state.confirming)
        assertNull(state.generated)
        assertEquals(0, store.events.size)
    }

    @Test
    fun withNothingPendingThereIsNothingToSettle() {
        pools.periodParts[period] = emptyList()

        val state = viewModel().uiState.value

        assertNull(state.content)
        assertEquals(BusinessError.NothingPending, state.error)
    }

    @Test
    fun confirmingEmitsTheEventOnceAndRequestsTheShare() {
        val vm = viewModel()
        vm.onAction(SettlementStatementUiAction.SelectFormat(StatementFormat.IMAGE))
        vm.onAction(SettlementStatementUiAction.AskConfirm)
        assertTrue(vm.uiState.value.confirming)
        assertEquals(0, store.events.size)

        vm.onAction(SettlementStatementUiAction.Confirm)

        val event = store.events.single().event as SettlementStatementGenerated
        assertEquals(StatementFormat.IMAGE, event.format)
        assertEquals(listOf(PartId("p1")), event.partIds)
        val state = vm.uiState.value
        assertFalse(state.confirming)
        assertNotNull(state.generated)
        assertEquals(state.generated!!.statementId, state.shareRequest!!.statementId)
        assertEquals(StatementFormat.IMAGE, state.shareRequest!!.format)
    }

    @Test
    fun aSecondConfirmDoesNotEmitAnotherEvent() {
        val vm = viewModel()
        vm.onAction(SettlementStatementUiAction.AskConfirm)
        vm.onAction(SettlementStatementUiAction.Confirm)
        vm.onAction(SettlementStatementUiAction.Confirm)

        assertEquals(1, store.events.size)
    }

    @Test
    fun aGeneratedStatementCanBeSharedAgainWithoutAnEvent() {
        val vm = viewModel()
        vm.onAction(SettlementStatementUiAction.AskConfirm)
        vm.onAction(SettlementStatementUiAction.Confirm)
        vm.onAction(SettlementStatementUiAction.ShareHandled)
        assertNull(vm.uiState.value.shareRequest)

        vm.onAction(SettlementStatementUiAction.SelectFormat(StatementFormat.IMAGE))
        vm.onAction(SettlementStatementUiAction.ShareAgain)

        assertEquals(1, store.events.size)
        assertEquals(StatementFormat.IMAGE, vm.uiState.value.shareRequest!!.format)
        assertEquals(vm.uiState.value.generated!!.statementId, vm.uiState.value.shareRequest!!.statementId)
    }

    @Test
    fun sharingAgainBeforeConfirmingDoesNothing() {
        val vm = viewModel()

        vm.onAction(SettlementStatementUiAction.ShareAgain)

        assertNull(vm.uiState.value.shareRequest)
        assertEquals(0, store.events.size)
    }

    @Test
    fun dismissingTheConfirmationKeepsThePeriodOpen() {
        val vm = viewModel()
        vm.onAction(SettlementStatementUiAction.AskConfirm)
        vm.onAction(SettlementStatementUiAction.DismissConfirm)

        assertFalse(vm.uiState.value.confirming)
        assertEquals(0, store.events.size)
    }

    @Test
    fun comingBackFromTheShareSheetDeletesTheTemporaryFiles() {
        val vm = viewModel()
        File(shareDir, "rendicion-1.pdf").writeText("x")

        vm.onAction(SettlementStatementUiAction.ShareClosed)

        assertEquals(emptyList<String>(), shareDir.list()!!.toList())
    }
}
