package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.ProjectionRebuilder
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.model.TableFingerprint
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.TableFingerprintSource
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ObserveNeedsReviewCountUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ReadRawEventsUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.TableFingerprintUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DevToolsViewModelTest {
    private class CountingRebuilder : ProjectionRebuilder {
        var rebuilds = 0
        override suspend fun rebuild(): Map<String, Int> {
            rebuilds++
            return mapOf("movement" to 2)
        }
    }

    private class Reviews(private val count: Int) : ReconciliationRepository {
        override suspend fun find(id: ReconciliationId): Reconciliation? = error("unused")
        override suspend fun openFor(accountId: AccountId): Reconciliation? = error("unused")
        override suspend fun remainingDifference(id: ReconciliationId): Money? = error("unused")
        override suspend fun isLinked(id: ReconciliationId, movementId: MovementId): Boolean = error("unused")
        override fun observeDetail(id: ReconciliationId): Flow<ReconciliationDetail?> = error("unused")
        override fun observeLatest(accountId: AccountId): Flow<Reconciliation?> = error("unused")
        override fun observeNeedsReviewCount(): Flow<Int> = flowOf(count)
    }

    private class Source(var tables: List<TableFingerprint>) : TableFingerprintSource {
        override suspend fun fingerprints() = tables
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val prompt = AuthPrompt("Confirma")
    private val store = FakeEventStore()
    private val authenticator = FakeAuthenticator()
    private val rebuilder = CountingRebuilder()
    private val source = Source(listOf(TableFingerprint("ledger_entries", 3, "aa")))
    private val ids: IdGenerator = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun seed(count: Int) = runBlocking {
        store.append("c", (1..count).map { NewEvent(AccountRenamed(AccountId("a1"), "n$it"), LocalDate.of(2026, 10, 25)) })
    }

    private fun viewModel(reviews: Int = 2) = DevToolsViewModel(
        VerifyEventChainUseCase(store),
        ReplayProjectionsUseCase(store, rebuilder, ids, clock),
        TableFingerprintUseCase(source),
        ReadRawEventsUseCase(store),
        ObserveNeedsReviewCountUseCase(Reviews(reviews)),
        authenticator,
        dispatcher,
    )

    private fun DevToolsViewModel.enter() = onAction(DevToolsUiAction.Authenticate(prompt))

    @Test
    fun nothingIsShownOrDoneBeforeTheCredential() {
        seed(3)
        authenticator.result = AuthResult.Failed
        val vm = viewModel()
        vm.enter()

        assertEquals(DevToolsStage.LOCKED, vm.uiState.value.stage)
        assertEquals(DevToolsMessage.AUTH_FAILED, vm.uiState.value.message)
        assertTrue(vm.uiState.value.events.isEmpty())
        vm.onAction(DevToolsUiAction.Replay)
        vm.onAction(DevToolsUiAction.VerifyChain)
        assertEquals(0, rebuilder.rebuilds)
        assertNull(vm.uiState.value.chain)
    }

    @Test
    fun theCredentialIsAskedEveryTimeEvenWithoutTheAppLock() {
        val vm = viewModel()
        vm.enter()
        assertEquals(1, authenticator.calls)
        assertEquals(DevToolsStage.READY, vm.uiState.value.stage)
    }

    @Test
    fun withoutAnyCredentialOnTheDeviceItDoesNotOpen() {
        authenticator.availability = AuthAvailability.NO_CREDENTIAL_ENROLLED
        val vm = viewModel()
        vm.enter()
        assertEquals(DevToolsStage.LOCKED, vm.uiState.value.stage)
        assertEquals(DevToolsMessage.NO_CREDENTIAL, vm.uiState.value.message)
    }

    @Test
    fun enteringShowsTheFirstEventsAndTheReconciliationsToReview() {
        seed(60)
        val vm = viewModel(reviews = 2)
        vm.enter()
        assertEquals(50, vm.uiState.value.events.size)
        assertTrue(vm.uiState.value.hasMoreEvents)
        assertEquals(2, vm.uiState.value.needsReviewCount)
        vm.onAction(DevToolsUiAction.LoadMoreEvents)
        assertEquals(60, vm.uiState.value.events.size)
        assertFalse(vm.uiState.value.hasMoreEvents)
        assertEquals((1L..60L).toList(), vm.uiState.value.events.map { it.sequence })
    }

    @Test
    fun verifyingTheChainShowsTheResult() {
        seed(3)
        val vm = viewModel()
        vm.enter()
        vm.onAction(DevToolsUiAction.VerifyChain)
        assertEquals(ChainVerification.Valid(3), vm.uiState.value.chain)
    }

    @Test
    fun reprojectingComparesTheTablesBeforeAndAfter() {
        seed(2)
        val vm = viewModel()
        vm.enter()
        source.tables = listOf(TableFingerprint("ledger_entries", 3, "aa"))
        vm.onAction(DevToolsUiAction.Replay)

        assertEquals(1, rebuilder.rebuilds)
        assertNotNull(vm.uiState.value.changes)
        assertTrue(vm.uiState.value.changes.orEmpty().none { it.changed })
        assertTrue("the replay leaves its own event", store.events.any { it.event.type == "ProjectionsRebuilt" && it.payload.contains("MANUAL") })
    }

    @Test
    fun reprojectingShowsWhichTableChanged() {
        seed(2)
        val vm = viewModel()
        vm.enter()
        val changing = object : TableFingerprintSource {
            var calls = 0
            override suspend fun fingerprints() = listOf(TableFingerprint("ledger_entries", 3, if (calls++ == 0) "aa" else "bb"))
        }
        val changingVm = DevToolsViewModel(
            VerifyEventChainUseCase(store), ReplayProjectionsUseCase(store, rebuilder, ids, clock), TableFingerprintUseCase(changing),
            ReadRawEventsUseCase(store), ObserveNeedsReviewCountUseCase(Reviews(0)), authenticator, dispatcher,
        )
        changingVm.enter()
        changingVm.onAction(DevToolsUiAction.Replay)
        assertEquals(listOf("ledger_entries"), changingVm.uiState.value.changes.orEmpty().filter { it.changed }.map { it.table })
    }
}
