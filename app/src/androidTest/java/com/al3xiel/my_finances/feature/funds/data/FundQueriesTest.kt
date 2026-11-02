package com.al3xiel.my_finances.feature.funds.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.fund.FundProjector
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.FundClosed
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.FundCreated
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.UntrackedSpendData
import com.al3xiel.my_finances.domain.model.event.UsageDayMarked
import com.al3xiel.my_finances.feature.funds.domain.model.FundEditFacts
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

/**
 * Las consultas de fondos sobre Room real, con los datos que dejan los proyectores:
 *  - `pasajes` (estimado S/ 5 al día): recarga de 50 vinculada a un ingreso, 2 días de uso, un gasto de 5, otro de 3
 *    anulado y un arqueo que cargó 5 sin anotar.
 *  - `comida` (detallado): recarga de 30 y un gasto de 12 desde otra cuenta.
 *  - `viejo`: recarga de 10 y ya cerrado (no aparta nada).
 */
@RunWith(AndroidJUnit4::class)
class FundQueriesTest {
    private lateinit var db: AppDatabase
    private lateinit var funds: FundRepositoryImpl

    private val cash = AccountId("cash")
    private val bbva = AccountId("bbva")
    private val pasajes = FundId("pasajes")
    private val comida = FundId("comida")
    private val viejo = FundId("viejo")
    private val transport = CategoryId("transport")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    private fun part(id: String, minor: Long, fund: FundId) = ExpensePartData(PartId(id), pen(minor), transport, emptyList(), null, fund)

    private val script: List<Pair<DomainEvent, LocalDate>> = listOf(
        AccountCreated(cash, "Efectivo", AccountType.CASH, CurrencyCode.PEN, null, pen(10_000)) to oct(1),
        AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(100_000)) to oct(1),
        FundCreated(pasajes, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(31)) to oct(1),
        FundCreated(comida, "Comida", ConsumptionMode.DETAILED, null, oct(1), null) to oct(1),
        FundCreated(viejo, "Viejo", ConsumptionMode.NONE, null, oct(1), null) to oct(1),
        FundToppedUp(pasajes, pen(5_000), MovementId("income1")) to oct(2),
        FundToppedUp(comida, pen(3_000), null) to oct(2),
        FundToppedUp(viejo, pen(1_000), null) to oct(2),
        UsageDayMarked(pasajes, oct(3)) to oct(3),
        UsageDayMarked(pasajes, oct(4)) to oct(4),
        ExpenseRegistered(MovementId("m1"), cash, pen(500), listOf(part("p1", 500, pasajes)), null, null) to oct(4),
        ExpenseRegistered(MovementId("m2"), cash, pen(300), listOf(part("p2", 300, pasajes)), null, null) to oct(5),
        MovementVoided(MovementId("m2"), "duplicado", null) to oct(5),
        ExpenseRegistered(MovementId("m3"), bbva, pen(1_200), listOf(part("p3", 1_200, comida)), null, null) to oct(6),
        FundCounted(pasajes, pen(4_000), UntrackedSpendData(MovementId("m9"), cash, transport, pen(500))) to oct(10),
        FundClosed(viejo, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null) to oct(12),
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val projectors: List<EventProjector> = listOf(MovementProjector(db.movementDao(), db.ledgerDao()), FundProjector(db.fundDao()))
        script.forEachIndexed { i, (event, on) ->
            val stored = StoredEvent(
                sequence = i + 1L, eventId = "e$i", schemaVersion = 1, correlationId = "c", payload = EventCodec.encode(event),
                occurredOn = on, recordedAt = Instant.ofEpochMilli(i + 1L), appVersion = "test", previousHash = "p", hash = "h", event = event,
            )
            projectors.sortedBy { it.order }.forEach { it.apply(stored) }
        }
        funds = FundRepositoryImpl(db.fundQueryDao())
    }

    @After fun tearDown() = db.close()

    @Test
    fun aFundComesWithWhatIsCalculatedFromItsPartsAndDays() = runBlocking {
        val overview = funds.find(pasajes)!!

        assertEquals("Pasajes", overview.fund.name)
        assertEquals(5_000L, overview.fund.allocated.amountMinor)
        assertEquals(500L, overview.fund.untrackedSpent.amountMinor)
        // Solo el gasto activo: el anulado no cuenta (D23).
        assertEquals(500L, overview.detailedSpent.amountMinor)
        assertEquals(2, overview.usageDays)
        assertEquals(oct(10), overview.lastCountOn)
        assertEquals(4_000L, overview.reserved.amountMinor)
        assertEquals(1_000L, overview.estimatedSpent!!.amountMinor)
        assertEquals(4_000L, overview.estimatedRemaining.amountMinor)
    }

    @Test
    fun anUnknownFundIsNull() = runBlocking {
        assertNull(funds.find(FundId("ghost")))
    }

    @Test
    fun theListHasActiveFundsFirstAndEachOneWithItsOwnFigures() = runBlocking {
        val list = funds.observeFunds().first()

        assertEquals(listOf("Comida", "Pasajes", "Viejo"), list.map { it.fund.name })
        assertEquals(listOf(FundStatus.ACTIVE, FundStatus.ACTIVE, FundStatus.CLOSED), list.map { it.fund.status })
        assertEquals(1_200L, list.first { it.fund.id == comida }.detailedSpent.amountMinor)
        assertEquals(1_800L, list.first { it.fund.id == comida }.reserved.amountMinor)
        assertNull(list.first { it.fund.id == comida }.lastCountOn)
        assertEquals(oct(12), list.first { it.fund.id == viejo }.fund.closedOn)
    }

    @Test
    fun theSummarySetsAsideOnlyActiveFundsAndLeavesTheRestAvailable() = runBlocking {
        val summary = funds.observeSummary().first()

        // Apartado: pasajes 4.000 + comida 1.800. Cuentas: efectivo 10.000 − 500 − 500 = 9.000 y BBVA 98.800.
        assertEquals(5_800L, summary.reserved.amountMinor)
        assertEquals(107_800L - 5_800L, summary.available.amountMinor)
        assertEquals(102_000L, funds.availableBalanceMinor())
    }

    @Test
    fun theReservedAmountOfAFundNeverGoesBelowZero() = runBlocking {
        db.fundDao().upsertFund(db.fundDao().fund("comida")!!.copy(allocatedMinor = 1_000))

        assertEquals(0L, funds.find(comida)!!.reserved.amountMinor)
        // 107.800 − 4.000 (pasajes) − 0 (comida)
        assertEquals(103_800L, funds.observeSummary().first().available.amountMinor)
    }

    @Test
    fun topUpsAreCountedPerLinkedIncome() = runBlocking {
        assertEquals(5_000L, funds.toppedUpFrom(MovementId("income1")).amountMinor)
        assertEquals(0L, funds.toppedUpFrom(MovementId("other")).amountMinor)
    }

    @Test
    fun editFactsTellWhatLocksTheEditor() = runBlocking {
        assertEquals(FundEditFacts(hasUsageDays = true, hasCounts = true, hasAssignedExpenses = true, latestActivityOn = oct(4)), funds.editFacts(pasajes))
        assertEquals(FundEditFacts(hasUsageDays = false, hasCounts = false, hasAssignedExpenses = true, latestActivityOn = oct(6)), funds.editFacts(comida))
        assertEquals(FundEditFacts(hasUsageDays = false, hasCounts = false, hasAssignedExpenses = false), funds.editFacts(viejo))
    }

    @Test
    fun usageDaysAndCountsOfAFund() = runBlocking {
        assertEquals(setOf(oct(3), oct(4)), funds.usageDays(pasajes))
        assertEquals(listOf(oct(3), oct(4)), funds.observeUsageDays(pasajes).first())
        val count = funds.observeCounts(pasajes).first().single()
        assertEquals(4_000L, count.declaredRemaining.amountMinor)
        assertEquals(1_000L, count.realSpent.amountMinor)
        assertEquals(1_000L, count.estimatedSpent!!.amountMinor)
        assertEquals(0L, count.deviation!!.amountMinor)
        assertEquals(oct(10), count.occurredOn)
    }

    @Test
    fun activeNamesSkipClosedFunds() = runBlocking {
        assertEquals(setOf("Pasajes", "Comida"), funds.activeNames().toSet())
    }
}
