package com.al3xiel.my_finances.data.projection

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** El reconstructor avisa a cada proyector: reset, beginReplay, todos los eventos, finishReplay (o abortReplay si falla). */
@RunWith(AndroidJUnit4::class)
class ProjectionRebuilderLifecycleTest {
    private lateinit var database: AppDatabase
    private val calls = mutableListOf<String>()

    private class Recorder(
        override val name: String,
        override val order: Int,
        private val calls: MutableList<String>,
        private val failOnApply: Boolean = false,
    ) : EventProjector {
        override val version = 1
        override suspend fun apply(event: StoredEvent) {
            if (failOnApply) error("boom")
            calls += "$name.apply"
        }
        override suspend fun reset() { calls += "$name.reset" }
        override suspend fun beginReplay() { calls += "$name.begin" }
        override suspend fun finishReplay() { calls += "$name.finish" }
        override suspend fun abortReplay() { calls += "$name.abort" }
    }

    private val event = AccountCreated(AccountId("a"), "A", AccountType.BANK, CurrencyCode.PEN, null, Money(0, CurrencyCode.PEN))
    private val stored = listOf(
        StoredEvent(1, "e1", 1, "c", EventCodec.encode(event), LocalDate.of(2026, 10, 1), Instant.EPOCH, "t", "p", "h", event),
        StoredEvent(2, "e2", 1, "c", EventCodec.encode(event), LocalDate.of(2026, 10, 1), Instant.EPOCH, "t", "p", "h", event),
    )
    private val store = object : EventStore {
        override suspend fun append(correlationId: String, events: List<NewEvent>) = emptyList<StoredEvent>()
        override suspend fun readAll() = stored
        override suspend fun readAfter(sequence: Long) = stored.filter { it.sequence > sequence }
        override suspend fun history(aggregateType: AggregateType, aggregateId: String) = emptyList<StoredEvent>()
    }
    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun eachProjectorGetsResetBeginEventsAndFinishInOrder() = runBlocking {
        val late = Recorder("late", 40, calls)
        val early = Recorder("early", 0, calls)
        ProjectionRebuilderImpl(database, setOf(late, early), store, clock).rebuild()

        assertEquals(
            listOf(
                "early.reset", "early.begin", "early.apply", "early.apply", "early.finish",
                "late.reset", "late.begin", "late.apply", "late.apply", "late.finish",
            ),
            calls,
        )
    }

    @Test
    fun aFailingProjectorIsToldToAbortAndTheErrorPropagates() = runBlocking {
        val failing = Recorder("bad", 0, calls, failOnApply = true)
        val result = runCatching { ProjectionRebuilderImpl(database, setOf(failing), store, clock).rebuild() }

        assertTrue(result.isFailure)
        assertEquals(listOf("bad.reset", "bad.begin", "bad.abort"), calls)
    }
}
