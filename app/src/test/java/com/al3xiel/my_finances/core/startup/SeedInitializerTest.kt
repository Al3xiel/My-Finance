package com.al3xiel.my_finances.core.startup

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.ProjectionRebuilder
import com.al3xiel.my_finances.domain.usecase.ProjectionVersionGuard
import com.al3xiel.my_finances.domain.usecase.ProjectionVersions
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.domain.usecase.SeedCatalogUseCase
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SeedInitializerTest {
    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val rebuilder = object : ProjectionRebuilder {
        override suspend fun rebuild() = mapOf("movement" to 4)
    }
    private var storedFails = false
    private val versions = object : ProjectionVersions {
        override val current = mapOf("movement" to 4)
        override suspend fun stored(): Map<String, Int> {
            check(!storedFails) { "no se pudo leer el estado de las proyecciones" }
            return mapOf("movement" to 3)
        }
    }

    private fun initializer(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = SeedInitializer(
        ProjectionVersionGuard(versions, store, ReplayProjectionsUseCase(store, rebuilder, ids, clock), com.al3xiel.my_finances.domain.usecase.FakeRestoreMarker()),
        SeedCatalogUseCase(EventRecorder(store, ids, clock), store),
        dispatcher,
    )

    private suspend fun withOldEvents() {
        store.append("c0", listOf(NewEvent(AccountRenamed(AccountId("a1"), "x"), LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun theVersionReplayRunsBeforeTheCatalogSeed() = runTest {
        withOldEvents()

        initializer(UnconfinedTestDispatcher(testScheduler)).start(this)

        val events = store.events.map { it.event }
        assertEquals(ProjectionsRebuilt(mapOf("movement" to 4), RebuildReason.VERSION_CHANGE), events[1])
        assertTrue("el sembrado va después del replay", events.drop(2).any { it is IncomeSourceCreated })
    }

    @Test
    fun aFailingVersionCheckDoesNotStopTheSeedNorTheApp() = runTest {
        withOldEvents()
        storedFails = true

        initializer(UnconfinedTestDispatcher(testScheduler)).start(this)

        val events = store.events.map { it.event }
        assertTrue(events.none { it is ProjectionsRebuilt })
        assertTrue(events.any { it is IncomeSourceCreated })
    }
}
