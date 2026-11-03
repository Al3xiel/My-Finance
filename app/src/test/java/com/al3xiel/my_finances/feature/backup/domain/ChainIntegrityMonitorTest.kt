package com.al3xiel.my_finances.feature.backup.domain

import com.al3xiel.my_finances.feature.backup.domain.usecase.ChainIntegrityMonitor
import com.al3xiel.my_finances.feature.backup.domain.usecase.ChainStatus
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ChainIntegrityMonitorTest {
    private val store = FakeEventStore()
    private val day = LocalDate.of(2026, 10, 25)

    private suspend fun seed() {
        store.append("c", listOf(NewEvent(AccountRenamed(AccountId("a1"), "x"), day), NewEvent(AccountRenamed(AccountId("a1"), "y"), day)))
    }

    private fun TestScope.monitor() = ChainIntegrityMonitor(VerifyEventChainUseCase(store), StandardTestDispatcher(testScheduler))

    @Test
    fun startsUnknownAndReportsAnIntactChain() = runTest {
        seed()
        val monitor = monitor()
        assertEquals(ChainStatus.Unknown, monitor.status.value)
        monitor.checkOnce()
        assertEquals(ChainStatus.Intact(2), monitor.status.value)
    }

    @Test
    fun reportsWhereTheChainBreaks() = runTest {
        seed()
        store.events[1] = store.events[1].copy(payload = "{\"tampered\":true}")
        val monitor = monitor()
        monitor.checkOnce()
        assertEquals(ChainStatus.Broken(2, ChainVerification.Reason.HASH_MISMATCH), monitor.status.value)
    }

    @Test
    fun runsOnlyOncePerProcess() = runTest {
        seed()
        val monitor = monitor()
        monitor.checkOnce()
        store.events[0] = store.events[0].copy(payload = "{\"tampered\":true}")
        monitor.checkOnce()
        assertEquals(ChainStatus.Intact(2), monitor.status.value)
    }

    @Test
    fun recheckLooksAgain() = runTest {
        seed()
        val monitor = monitor()
        monitor.checkOnce()
        store.events[0] = store.events[0].copy(payload = "{\"tampered\":true}")
        assertEquals(ChainStatus.Broken(1, ChainVerification.Reason.HASH_MISMATCH), monitor.recheck())
        assertEquals(ChainStatus.Broken(1, ChainVerification.Reason.HASH_MISMATCH), monitor.status.value)
    }
}
