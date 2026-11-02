package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ProjectionVersionGuardTest {
    private val store = FakeEventStore()
    private var rebuilds = 0
    private val rebuilder = object : ProjectionRebuilder {
        override suspend fun rebuild(): Map<String, Int> {
            rebuilds++
            return current
        }
    }
    private var current = mapOf("catalog" to 1, "movement" to 4)
    private var stored: Map<String, Int> = current
    private val versions = object : ProjectionVersions {
        override val current get() = this@ProjectionVersionGuardTest.current
        override suspend fun stored() = this@ProjectionVersionGuardTest.stored
    }
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val replay = ReplayProjectionsUseCase(store, rebuilder, SequentialIdGenerator(), clock)
    private val guard = ProjectionVersionGuard(versions, store, replay)

    private suspend fun withEvents() {
        store.append("c0", listOf(NewEvent(AccountRenamed(AccountId("a1"), "x"), LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun sameVersionsDoNothing() = runTest {
        withEvents()

        assertFalse(guard())

        assertEquals(0, rebuilds)
        assertEquals(1, store.events.size)
    }

    @Test
    fun aDifferentVersionWithEventsReplaysWithTheVersionChangeReason() = runTest {
        withEvents()
        stored = mapOf("catalog" to 1, "movement" to 3)

        assertTrue(guard())

        assertEquals(1, rebuilds)
        assertEquals(ProjectionsRebuilt(current, RebuildReason.VERSION_CHANGE), store.events.last().event)
    }

    @Test
    fun aProjectorThatWasNeverStoredCountsAsAChange() = runTest {
        withEvents()
        stored = mapOf("catalog" to 1, "movement" to 4).minus("movement")

        assertTrue(guard())

        assertEquals(1, rebuilds)
    }

    @Test
    fun withoutEventsThereIsNothingToRebuild() = runTest {
        stored = emptyMap()

        assertFalse(guard())

        assertEquals(0, rebuilds)
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun runsOnlyOncePerVersionChange() = runTest {
        withEvents()
        stored = mapOf("catalog" to 1, "movement" to 3)

        assertTrue(guard())
        stored = current // el replay deja las versiones al día
        assertFalse(guard())

        assertEquals(1, rebuilds)
    }
}
