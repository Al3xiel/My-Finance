package com.al3xiel.my_finances.data.eventstore

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Las consultas de lectura que usa el respaldo, sobre Room real. */
@RunWith(AndroidJUnit4::class)
class EventDaoBackupQueriesTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: EventDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        dao = db.eventDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(i: Int, type: String = "CategoryCreated", aggregateId: String = "c$i", recordedAt: Long = 1_000L + i) {
        dao.insert(
            EventEntity(
                eventId = "e$i", type = type, schemaVersion = 1, aggregateType = "CATEGORY", aggregateId = aggregateId, correlationId = "k",
                payload = "{}", occurredOn = "2026-10-01", recordedAt = recordedAt, appVersion = "test",
                previousHash = if (i == 1) "GENESIS" else "h${i - 1}", hash = "h$i",
            ),
        )
    }

    @Test
    fun anEmptyLogHasNothing() = runBlocking {
        assertEquals(0L, dao.count())
        assertNull(dao.lastHash())
        assertNull(dao.firstEventAt())
        assertNull(dao.lastBackupAt())
        assertEquals(emptyList<EventEntity>(), dao.page(0, 10))
    }

    @Test
    fun pagesFollowTheSequenceWithoutRepeatingOrSkipping() = runBlocking {
        (1..5).forEach { add(it) }
        val first = dao.page(0, 2)
        val second = dao.page(first.last().sequence, 2)
        val third = dao.page(second.last().sequence, 2)
        assertEquals(listOf("e1", "e2"), first.map { it.eventId })
        assertEquals(listOf("e3", "e4"), second.map { it.eventId })
        assertEquals(listOf("e5"), third.map { it.eventId })
        assertEquals(emptyList<EventEntity>(), dao.page(third.last().sequence, 2))
    }

    @Test
    fun countLastHashAndFirstEventComeFromTheLog() = runBlocking {
        add(1, recordedAt = 500L)
        add(2, recordedAt = 900L)
        assertEquals(2L, dao.count())
        assertEquals("h2", dao.lastHash())
        assertEquals(500L, dao.firstEventAt())
    }

    @Test
    fun theLastBackupIsTheNewestOfCreatedAndRestored() = runBlocking {
        add(1)
        add(2, type = "BackupCreated", aggregateId = "b1", recordedAt = 5_000L)
        add(3, type = "BackupRestored", aggregateId = "b2", recordedAt = 7_000L)
        add(4, recordedAt = 9_000L)
        assertEquals(7_000L, dao.lastBackupAt())
    }

    @Test
    fun seedEventsAreNotOwnData() = runBlocking {
        add(1, type = "IncomeSourceCreated", aggregateId = "sys-income")
        add(2, type = "CategoryCreated", aggregateId = "sys-food")
        add(3, type = "ProjectionsRebuilt", aggregateId = "projections")
        assertEquals(0L, dao.countNonSeed(listOf("sys-income"), listOf("sys-food")))
    }

    @Test
    fun anythingElseIsOwnData() = runBlocking {
        add(1, type = "CategoryCreated", aggregateId = "sys-food")
        add(2, type = "CategoryCreated", aggregateId = "mine")
        add(3, type = "BackupCreated", aggregateId = "b1")
        assertEquals(2L, dao.countNonSeed(listOf("sys-income"), listOf("sys-food")))
    }
}
