package com.al3xiel.my_finances.data.eventstore

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.ProjectionStateEntity
import com.al3xiel.my_finances.data.projection.ProjectionVersionsImpl
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Guardar eventos avanza `last_sequence`, pero la versión guardada solo la cambia un replay (D32). */
@RunWith(AndroidJUnit4::class)
class EventStoreProgressTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var versions: ProjectionVersionsImpl

    private val projector = object : EventProjector {
        override val name = "test"
        override val version = 2
        override suspend fun apply(event: StoredEvent) = Unit
        override suspend fun reset() = Unit
    }

    private val ids = object : IdGenerator {
        private var n = 0
        override fun newId() = "id-${n++}"
    }

    private fun created() = NewEvent(
        AccountCreated(AccountId("a1"), "BBVA", AccountType.BANK, CurrencyCode.PEN, null, Money(0, CurrencyCode.PEN)),
        LocalDate.of(2026, 11, 2),
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        store = EventStoreImpl(database, setOf(projector), ids, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC), "1.0")
        versions = ProjectionVersionsImpl(setOf(projector), database.projectionStateDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun theFirstEventStoresTheCurrentVersionAndTheLastSequence() = runBlocking {
        store.append("c", listOf(created()))

        val state = database.projectionStateDao().state("test")!!
        assertEquals(2, state.projectorVersion)
        assertEquals(1L, state.lastSequence)
        assertEquals(mapOf("test" to 2), versions.stored())
    }

    @Test
    fun anOlderStoredVersionSurvivesNewEventsSoTheStartupCanSeeIt() = runBlocking {
        database.projectionStateDao().upsert(ProjectionStateEntity("test", 1, 0, 500L))

        store.append("c", listOf(created()))

        val state = database.projectionStateDao().state("test")!!
        assertEquals(1, state.projectorVersion)
        assertEquals(1L, state.lastSequence)
        assertEquals(500L, state.rebuiltAt)
        assertEquals(mapOf("test" to 1), versions.stored())
        assertEquals(mapOf("test" to 2), versions.current)
    }

    @Test
    fun withoutAnyEventNothingIsStored() = runBlocking {
        assertEquals(emptyMap<String, Int>(), versions.stored())
    }
}
