package com.al3xiel.my_finances.feature.diagnostics.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.projection.ProjectorSetFactory
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.feature.diagnostics.domain.model.SnapshotCut
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** El lector con corte entrega saldos y asientos de reproducir solo los eventos elegidos, no los de la base de hoy. */
@RunWith(AndroidJUnit4::class)
class RoomProjectionSnapshotReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pen = CurrencyCode.PEN
    private lateinit var db: AppDatabase
    private lateinit var reader: RoomProjectionSnapshotReader
    private val sequences = mutableMapOf<String, Long>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        reader = RoomProjectionSnapshotReader(db)
        val store = EventStoreImpl(db, ProjectorSetFactory(db).create(), UuidIdGenerator(), Clock.systemUTC(), "test")
        val day = LocalDate.of(2026, 10, 25)
        listOf(
            "person" to PersonCreated(PersonId("p1"), "Ana"),
            "source" to IncomeSourceCreated(IncomeSourceId("s1"), "Empresa", PersonId("p1")),
            "a1" to AccountCreated(AccountId("a1"), "Cuenta 1", AccountType.BANK, pen, null, Money(500, pen)),
            "m1" to IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(1_000, pen), IncomeSourceId("s1"), null, null),
            "a2" to AccountCreated(AccountId("a2"), "Cuenta 2", AccountType.BANK, pen, null, Money(300, pen)),
            "m2" to IncomeRegistered(MovementId("m2"), AccountId("a1"), Money(400, pen), IncomeSourceId("s1"), null, null),
        ).forEach { (key, event) -> sequences[key] = store.append("c", listOf(NewEvent(event, day))).single().sequence }
    }

    @After
    fun tearDown() = db.close()

    private fun cut(vararg keys: String) = SnapshotCut(keys.map { sequences.getValue(it) }.toSet())

    @Test
    fun withoutACutItIsTheStateOfToday() = runBlocking {
        val now = reader.read(null, null, includeOrigin = false)
        assertEquals(mapOf("a1" to 1_900L, "a2" to 300L), now.accountBalances)
    }

    @Test
    fun aCutKeepsOnlyTheEntriesOfTheChosenEventsAndLeavesAnAccountWithoutEntriesAtZero() = runBlocking {
        val snapshot = reader.read(null, null, includeOrigin = false, cut = cut("a1", "m1"))

        assertEquals(mapOf("a1" to 1_500L, "a2" to 0L), snapshot.accountBalances)
        assertEquals(listOf(sequences.getValue("a1"), sequences.getValue("m1")), snapshot.ledger.map { it.sequence })
        assertEquals(listOf(500L, 1_500L), snapshot.ledger.map { it.balanceMinor })
    }

    @Test
    fun anAccountFilterKeepsOnlyThatAccountEvenWithAWideCut() = runBlocking {
        val snapshot = reader.read(AccountId("a1"), null, includeOrigin = false, cut = cut("a1", "m1", "a2", "m2"))

        assertEquals(mapOf("a1" to 1_900L), snapshot.accountBalances)
        assertEquals(setOf("a1"), snapshot.ledger.map { it.accountId }.toSet())
    }
}
