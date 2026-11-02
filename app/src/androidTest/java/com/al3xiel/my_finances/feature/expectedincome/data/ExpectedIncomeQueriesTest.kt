package com.al3xiel.my_finances.feature.expectedincome.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeProjector
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeConfirmed
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Las consultas de ingresos esperados sobre Room real, con los datos que dejan los proyectores. Hoy es 2026-11-10:
 *  - `e1` (28 oct–5 nov) sin confirmar → vencido.
 *  - `e2` confirmado con `m1` (activo) y su siguiente `e3` (28 nov–5 dic) → programado.
 *  - `e4` (1–5 oct) confirmado con `m2`, luego anulado → vuelve a pendiente (vencido).
 *  - `e5` cancelado.
 */
@RunWith(AndroidJUnit4::class)
class ExpectedIncomeQueriesTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: ExpectedIncomeRepositoryImpl

    private val bbva = AccountId("bbva")
    private val sueldo = IncomeSourceId("s1")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun id(value: String) = ExpectedIncomeId(value)
    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun scheduled(id: String, from: LocalDate, to: LocalDate) =
        ExpectedIncomeScheduled(id(id), sueldo, bbva, pen(200_000), from, to, true)

    private val script: List<Pair<DomainEvent, LocalDate>> = listOf(
        AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(100_000)) to day(10, 1),
        scheduled("e1", day(10, 28), day(11, 5)) to day(10, 1),
        scheduled("e2", day(9, 28), day(10, 5)) to day(9, 1),
        scheduled("e4", day(10, 1), day(10, 5)) to day(9, 1),
        scheduled("e5", day(10, 10), day(10, 15)) to day(9, 1),
        IncomeRegistered(MovementId("m1"), bbva, pen(200_000), sueldo, null, null) to day(10, 3),
        ExpectedIncomeConfirmed(id("e2"), MovementId("m1"), id("e3")) to day(10, 3),
        scheduled("e3", day(11, 28), day(12, 5)) to day(10, 3),
        IncomeRegistered(MovementId("m2"), bbva, pen(200_000), sueldo, null, null) to day(10, 4),
        ExpectedIncomeConfirmed(id("e4"), MovementId("m2"), null) to day(10, 4),
        MovementVoided(MovementId("m2"), "monto equivocado", null) to day(10, 6),
        ExpectedIncomeCancelled(id("e5"), "ya no aplica") to day(10, 20),
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val projectors: List<EventProjector> =
            listOf(MovementProjector(db.movementDao(), db.ledgerDao()), ExpectedIncomeProjector(db.expectedIncomeDao()))
        script.forEachIndexed { i, (event, on) ->
            val stored = StoredEvent(
                sequence = i + 1L, eventId = "e$i", schemaVersion = 1, correlationId = "c", payload = EventCodec.encode(event),
                occurredOn = on, recordedAt = Instant.ofEpochMilli(i + 1L), appVersion = "test", previousHash = "p", hash = "h", event = event,
            )
            projectors.sortedBy { it.order }.forEach { it.apply(stored) }
        }
        val clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC)
        repository = ExpectedIncomeRepositoryImpl(db.expectedIncomeQueryDao(), clock)
    }

    @After fun tearDown() = db.close()

    @Test
    fun theListCarriesTheEffectiveStatus() = runBlocking {
        val byId = repository.observeAll().first().associateBy { it.id.value }

        assertEquals(ExpectedIncomeStatus.OVERDUE, byId.getValue("e1").status)
        assertEquals(ExpectedIncomeStatus.CONFIRMED, byId.getValue("e2").status)
        assertEquals(ExpectedIncomeStatus.SCHEDULED, byId.getValue("e3").status)
        assertEquals(ExpectedIncomeStatus.OVERDUE, byId.getValue("e4").status)
        assertEquals(ExpectedIncomeStatus.CANCELLED, byId.getValue("e5").status)
        assertEquals("ya no aplica", byId.getValue("e5").cancelReason)
        assertEquals(listOf("e2", "e4", "e5", "e1", "e3"), repository.observeAll().first().map { it.id.value })
    }

    @Test
    fun pendingOnesOfAnAccountAndSourceIncludeTheOnesWhoseIncomeWasVoided() = runBlocking {
        assertEquals(listOf("e4", "e1", "e3"), repository.pendingFor(bbva, sueldo).map { it.id.value })
        assertEquals(emptyList<String>(), repository.pendingFor(bbva, IncomeSourceId("other")).map { it.id.value })
    }

    @Test
    fun overdueAndNextLookAtTheRangeEnd() = runBlocking {
        assertEquals(listOf("e4", "e1"), repository.overdue().map { it.id.value })
        assertEquals("e3", repository.next()?.id?.value)
    }

    @Test
    fun theConfirmedOneIsFoundByItsMovementWhileTheIncomeIsActive() = runBlocking {
        assertEquals("e2", repository.confirmedBy(MovementId("m1"))?.id?.value)
        assertEquals("e3", repository.find(id("e2"))?.nextExpectedId?.value)
        assertNull(repository.confirmedBy(MovementId("m2")))
        assertNull(repository.find(id("e4"))?.confirmedMovementId)
    }
}
