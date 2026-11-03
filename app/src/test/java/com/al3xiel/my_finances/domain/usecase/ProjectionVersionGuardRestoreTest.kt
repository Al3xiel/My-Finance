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
import org.junit.Assert.fail
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ProjectionVersionGuardRestoreTest {
    private val store = FakeEventStore()
    private val marker = FakeRestoreMarker(pending = true)
    private var rebuilds = 0
    private var rebuildFails = false
    private val current = mapOf("catalog" to 1)
    private var stored: Map<String, Int> = emptyMap()
    private val versions = object : ProjectionVersions {
        override val current get() = this@ProjectionVersionGuardRestoreTest.current
        override suspend fun stored() = stored
    }
    private val rebuilder = object : ProjectionRebuilder {
        override suspend fun rebuild(): Map<String, Int> {
            if (rebuildFails) error("boom")
            rebuilds++
            return current
        }
    }
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val guard = ProjectionVersionGuard(
        versions, store, ReplayProjectionsUseCase(store, rebuilder, SequentialIdGenerator(), clock), marker,
    )

    private suspend fun withEvents() {
        store.append("c0", listOf(NewEvent(AccountRenamed(AccountId("a1"), "x"), LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun aPendingRestoreReplaysWithTheRestoreReasonAndClearsTheMarker() = runTest {
        withEvents()

        assertTrue(guard())

        assertEquals(ProjectionsRebuilt(current, RebuildReason.RESTORE), store.events.last().event)
        assertFalse(marker.pending)
    }

    @Test
    fun aPendingRestoreReplaysEvenIfTheVersionsMatch() = runTest {
        withEvents()
        stored = current

        assertTrue(guard())

        assertEquals(RebuildReason.RESTORE, (store.events.last().event as ProjectionsRebuilt).reason)
    }

    @Test
    fun aFailedReplayKeepsTheMarkerForTheNextStart() = runTest {
        withEvents()
        rebuildFails = true

        try {
            guard()
            fail("expected the replay to fail")
        } catch (_: IllegalStateException) {
        }

        assertTrue(marker.pending)
    }

    @Test
    fun withoutEventsTheMarkerIsClearedAndNothingReplays() = runTest {
        assertFalse(guard())

        assertEquals(0, rebuilds)
        assertFalse(marker.pending)
    }

    @Test
    fun withoutAMarkerTheReasonStaysVersionChange() = runTest {
        withEvents()
        marker.pending = false

        assertTrue(guard())

        assertEquals(RebuildReason.VERSION_CHANGE, (store.events.last().event as ProjectionsRebuilt).reason)
    }
}
