package com.al3xiel.my_finances.feature.diagnostics.data

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
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Re-proyectar una copia de los eventos con el código actual da lo mismo que la base viva (sin eventos nuevos en la copia). */
@RunWith(AndroidJUnit4::class)
class WorkspaceReplayEqualsLiveTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var live: AppDatabase
    private lateinit var copy: AppDatabase

    @Before
    fun setUp() {
        live = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        copy = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        live.close()
        copy.close()
    }

    @Test
    fun replayOfACopyEqualsTheLiveProjections() = runBlocking {
        val clock = Clock.systemUTC()
        val store = EventStoreImpl(live, ProjectorSetFactory(live).create(), UuidIdGenerator(), clock, "test")
        val day = LocalDate.of(2026, 10, 25)
        val pen = CurrencyCode.PEN
        store.append(
            "c",
            listOf(
                PersonCreated(PersonId("p1"), "Ana"),
                AccountCreated(AccountId("a1"), "Cuenta", AccountType.BANK, pen, null, Money(500, pen)),
                IncomeSourceCreated(IncomeSourceId("s1"), "Empresa", PersonId("p1")),
                IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(1_000, pen), IncomeSourceId("s1"), null, null),
            ).map { NewEvent(it, day) },
        )
        copy.eventDao().insertRaw(live.eventDao().all())

        WorkspaceProjector(clock, "test").replay(copy)

        val expected = RoomProjectionSnapshotReader(live).read(null, null, includeOrigin = true)
        val actual = RoomProjectionSnapshotReader(copy).read(null, null, includeOrigin = true)
        assertEquals(expected.accountBalances, actual.accountBalances)
        assertEquals(expected.ledger, actual.ledger)
        assertEquals(expected.originBalances, actual.originBalances)
        assertEquals(1_500L, actual.accountBalances["a1"])
        assertEquals(4, copy.eventDao().all().size)
    }
}
