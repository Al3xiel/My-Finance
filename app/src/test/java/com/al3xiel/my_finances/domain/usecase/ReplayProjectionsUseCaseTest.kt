package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReplayProjectionsUseCaseTest {
    private val store = FakeEventStore()
    private val calls = mutableListOf<String>()
    private val rebuilder = object : ProjectionRebuilder {
        override suspend fun rebuild(): Map<String, Int> {
            calls += "rebuild:${store.events.size}"
            return mapOf("catalog" to 1)
        }
    }
    private val ids = object : IdGenerator {
        override fun newId() = "corr-1"
    }
    private val clock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC)
    private val replay = ReplayProjectionsUseCase(store, rebuilder, ids, clock)

    @Test
    fun rebuildsFromTheExistingEventsThenRecordsTheAudit() = runTest {
        store.append("c0", listOf(NewEvent(AccountRenamed(AccountId("a1"), "x"), LocalDate.of(2026, 10, 25))))

        replay("manual")

        assertEquals(listOf("rebuild:1"), calls)
        val audit = store.events.last()
        assertEquals(ProjectionsRebuilt(mapOf("catalog" to 1), "manual"), audit.event)
        assertEquals(LocalDate.of(2026, 10, 26), audit.occurredOn)
        assertEquals("corr-1", audit.correlationId)
        assertEquals(2, store.events.size)
    }
}
