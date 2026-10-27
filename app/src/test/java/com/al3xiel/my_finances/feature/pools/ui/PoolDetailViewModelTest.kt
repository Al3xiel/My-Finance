package com.al3xiel.my_finances.feature.pools.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolArchived
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolDetail
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.repository.ReceivablesRepository
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import com.al3xiel.my_finances.feature.pools.domain.usecase.ArchivePoolUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import com.al3xiel.my_finances.feature.pools.domain.model.Receivables
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

class PoolDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val dogs = PoolId("dogs")
    private val details = MutableStateFlow<Map<PoolId, PoolDetail>>(emptyMap())
    private val receivables = object : ReceivablesRepository {
        override fun observeReceivables(): Flow<Receivables> = throw UnsupportedOperationException()
        override fun observePoolDetail(poolId: PoolId): Flow<PoolDetail?> = details.map { it[poolId] }
    }
    private val store = FakeEventStore()
    private val recorder = EventRecorder(store, SequentialIdGenerator(), Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC))
    private val pools = FakePoolRepository()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun detail(pending: Long = 0): PoolDetail {
        val pool = Pool(dogs, "Perros – mamá", PersonId("mom"), PoolKind.PERMANENT, null, emptyList(), false)
        pools.pools[dogs] = pool
        pools.pending[dogs] = pending
        val open = PoolPeriod(PoolPeriodId("dogs-p2"), dogs, LocalDate.of(2026, 10, 31), null, PoolPeriodStatus.OPEN, null, pen(600), pen(0))
        val closed = PoolPeriod(PoolPeriodId("dogs-p1"), dogs, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), PoolPeriodStatus.STATEMENT_SENT, null, pen(2_000), pen(1_500))
        return PoolDetail(pool, "Mamá", listOf(open, closed), pen(pending))
    }

    private fun viewModel() = PoolDetailViewModel(dogs.value, receivables, ArchivePoolUseCase(pools, recorder))

    @Test
    fun showsThePoolWithItsPeriodsAndTheOpenOne() {
        details.value = mapOf(dogs to detail(1_100))

        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertFalse(state.notFound)
        assertEquals("Perros – mamá", state.detail!!.pool.name)
        assertEquals(PoolPeriodId("dogs-p2"), state.detail!!.currentPeriod!!.id)
        assertEquals(2, state.detail!!.periods.size)
    }

    @Test
    fun anUnknownPoolIsNotFound() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertTrue(state.notFound)
        assertNull(state.detail)
    }

    @Test
    fun archivingWithPendingShowsANoticeAndRecordsNothing() {
        details.value = mapOf(dogs to detail(pending = 500))
        val vm = viewModel()

        vm.onAction(PoolDetailUiAction.Archive)

        assertEquals(BusinessError.PendingNotZero, vm.uiState.value.notice)
        assertEquals(0, store.events.size)

        vm.onAction(PoolDetailUiAction.DismissNotice)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun archivingWithNothingPendingRecordsTheEvent() {
        details.value = mapOf(dogs to detail(pending = 0))
        val vm = viewModel()

        vm.onAction(PoolDetailUiAction.Archive)

        assertEquals(ReimbursementPoolArchived(dogs), store.events.single().event)
        assertNull(vm.uiState.value.notice)
    }
}
