package com.al3xiel.my_finances.data.eventstore

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountArchived
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class EventStoreTest {
    private lateinit var database: AppDatabase
    private val seen = mutableListOf<StoredEvent>()
    private var failOn: Long? = null

    private val projector = object : EventProjector {
        override val name = "test"
        override val version = 1
        override suspend fun apply(event: StoredEvent) {
            seen += event
            if (event.sequence == failOn) error("boom")
        }
        override suspend fun reset() = seen.clear()
    }

    private val ids = object : IdGenerator {
        private var n = 0
        override fun newId() = "id-${n++}"
    }

    private val day = LocalDate.of(2026, 10, 25)
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
    private val a1 = AccountId("a1")

    private lateinit var store: EventStoreImpl

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        store = EventStoreImpl(database, setOf(projector), ids, clock, "1.0")
    }

    @After
    fun tearDown() = database.close()

    private fun created() = NewEvent(
        AccountCreated(a1, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, Money(0, CurrencyCode.PEN)),
        day,
    )

    @Test
    fun chainsHashesFromGenesisAndKeepsSequenceGapless() = runBlocking {
        store.append("c1", listOf(created(), NewEvent(AccountRenamed(a1, "BBVA 2"), day)))
        store.append("c2", listOf(NewEvent(AccountArchived(a1), day)))

        val all = store.readAll()
        assertEquals(listOf(1L, 2L, 3L), all.map { it.sequence })
        assertEquals(EventHasher.GENESIS, all[0].previousHash)
        assertEquals(all[0].hash, all[1].previousHash)
        assertEquals(all[1].hash, all[2].previousHash)
        all.forEach { assertEquals(EventHasher.hash(it), it.hash) }
    }

    @Test
    fun projectorsSeeEveryEventInOrder() = runBlocking {
        store.append("c1", listOf(created(), NewEvent(AccountRenamed(a1, "x"), day)))
        assertEquals(listOf(1L, 2L), seen.map { it.sequence })
    }

    @Test
    fun failingProjectorRollsBackTheWholeAppend() = runBlocking {
        store.append("c1", listOf(created()))
        failOn = 3
        assertThrows(IllegalStateException::class.java) {
            runBlocking { store.append("c2", listOf(NewEvent(AccountRenamed(a1, "x"), day), NewEvent(AccountArchived(a1), day))) }
        }
        assertEquals(1, store.readAll().size)
        failOn = null
        store.append("c3", listOf(NewEvent(AccountRenamed(a1, "y"), day)))
        assertEquals(listOf(1L, 2L), store.readAll().map { it.sequence })
    }

    @Test
    fun updateAndDeleteAreAborted() = runBlocking {
        store.append("c1", listOf(created()))
        val sqlite = database.openHelper.writableDatabase
        assertThrows(SQLiteException::class.java) { sqlite.execSQL("UPDATE events SET type = 'X'") }
        assertThrows(SQLiteException::class.java) { sqlite.execSQL("DELETE FROM events") }
        assertEquals(1, store.readAll().size)
    }
}
