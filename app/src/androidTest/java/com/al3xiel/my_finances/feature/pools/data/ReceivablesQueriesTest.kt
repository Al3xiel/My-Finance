package com.al3xiel.my_finances.feature.pools.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.loan.LoanProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodEntity
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodRefresher
import com.al3xiel.my_finances.data.projection.pool.PoolProjector
import com.al3xiel.my_finances.data.projection.pool.ReimbursementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.ReimbursementId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AllocationData
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.LoanGranted
import com.al3xiel.my_finances.domain.model.event.LoanRepaymentReceived
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.model.event.ReimbursementReceived
import com.al3xiel.my_finances.domain.model.event.SettlementStatementGenerated
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.feature.loans.data.LoanRepositoryImpl
import com.al3xiel.my_finances.feature.people.data.PersonDetailRepositoryImpl
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
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
 * Las consultas de Por cobrar, cúmulos y personas sobre Room real, con los datos que dejan los proyectores:
 *  - `dogs` (responsable Mamá): p1 120 y p2 80 en el periodo 1, reembolso de 150, rendición, p4 60 en el periodo 2.
 *  - `trip` (responsable Pepe): p3 50 anulado.
 *  - Préstamo a Pepe de 50 con 20 pagados.
 */
@RunWith(AndroidJUnit4::class)
class ReceivablesQueriesTest {
    private lateinit var db: AppDatabase
    private lateinit var pools: PoolRepositoryImpl
    private lateinit var receivables: ReceivablesRepositoryImpl
    private lateinit var people: PersonDetailRepositoryImpl
    private lateinit var loans: LoanRepositoryImpl

    private val bbva = AccountId("bbva")
    private val mom = PersonId("mom")
    private val pepe = PersonId("pepe")
    private val dogs = PoolId("dogs")
    private val trip = PoolId("trip")
    private val pets = CategoryId("pets")
    private val dogTag = TagId("dog")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun part(id: String, minor: Long, pool: PoolId) =
        ExpensePartData(PartId(id), pen(minor), pets, if (id == "p4") listOf(dogTag) else emptyList(), pool, null)

    private val script: List<Pair<DomainEvent, LocalDate>> = listOf(
        PersonCreated(mom, "Mamá") to LocalDate.of(2026, 9, 1),
        PersonCreated(pepe, "Pepe") to LocalDate.of(2026, 9, 1),
        CategoryCreated(pets, "Mascotas", "pets", "#FFFFFF", null) to LocalDate.of(2026, 9, 1),
        TagCreated(dogTag, "Perros", TagKind.TOPIC, null) to LocalDate.of(2026, 9, 1),
        AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(100_000)) to LocalDate.of(2026, 9, 1),
        ReimbursementPoolCreated(dogs, "Perros – mamá", mom, PoolKind.PERMANENT, pets, listOf(dogTag)) to LocalDate.of(2026, 10, 1),
        ReimbursementPoolCreated(trip, "Viaje", pepe, PoolKind.EVENT, null, emptyList()) to LocalDate.of(2026, 10, 1),
        ExpenseRegistered(MovementId("m1"), bbva, pen(12_000), listOf(part("p1", 12_000, dogs)), null, null) to LocalDate.of(2026, 10, 3),
        ExpenseRegistered(MovementId("m2"), bbva, pen(8_000), listOf(part("p2", 8_000, dogs)), null, null) to LocalDate.of(2026, 10, 12),
        ExpenseRegistered(MovementId("m3"), bbva, pen(5_000), listOf(part("p3", 5_000, trip)), null, null) to LocalDate.of(2026, 10, 14),
        ReimbursementReceived(
            ReimbursementId("r1"), MovementId("mr1"), mom, bbva, pen(15_000), AllocationStrategy.OLDEST_FIRST,
            listOf(AllocationData(PartId("p1"), pen(12_000)), AllocationData(PartId("p2"), pen(3_000))), null,
        ) to LocalDate.of(2026, 10, 20),
        SettlementStatementGenerated(StatementId("s1"), dogs, PoolPeriodId("dogs-p1"), listOf(PartId("p2")), pen(5_000), StatementFormat.PDF) to LocalDate.of(2026, 10, 31),
        ExpenseRegistered(MovementId("m4"), bbva, pen(6_000), listOf(part("p4", 6_000, dogs)), null, null) to LocalDate.of(2026, 11, 2),
        MovementVoided(MovementId("m3"), "error", null) to LocalDate.of(2026, 11, 3),
        LoanGranted(LoanId("l1"), MovementId("ml1"), pepe, bbva, pen(5_000), null) to LocalDate.of(2026, 11, 4),
        LoanRepaymentReceived(com.al3xiel.my_finances.domain.model.RepaymentId("rp1"), MovementId("mrp1"), LoanId("l1"), bbva, pen(2_000)) to LocalDate.of(2026, 11, 5),
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val refresher = PoolPeriodRefresher(db.poolDao())
        val poster = LedgerPoster(db.movementDao(), db.ledgerDao())
        val projectors: List<EventProjector> = listOf(
            CatalogProjector(db.catalogDao()),
            MovementProjector(db.movementDao(), db.ledgerDao()),
            PoolProjector(db.poolDao(), refresher),
            ReimbursementProjector(db.reimbursementDao(), poster, refresher),
            LoanProjector(db.loanDao(), poster),
        ).sortedBy { it.order }
        script.forEachIndexed { i, (event, on) ->
            val stored = StoredEvent(
                sequence = i + 1L, eventId = "e$i", schemaVersion = 1, correlationId = "c", payload = EventCodec.encode(event),
                occurredOn = on, recordedAt = Instant.ofEpochMilli(i + 1L), appVersion = "test", previousHash = "p", hash = "h", event = event,
            )
            projectors.forEach { it.apply(stored) }
        }
        pools = PoolRepositoryImpl(db.poolQueryDao())
        receivables = ReceivablesRepositoryImpl(db.poolQueryDao())
        people = PersonDetailRepositoryImpl(db.poolQueryDao(), db.personQueryDao())
        loans = LoanRepositoryImpl(db.loanQueryDao())
    }

    @After fun tearDown() = db.close()

    @Test
    fun poolBasics() = runBlocking {
        assertEquals("Perros – mamá", pools.find(dogs)!!.name)
        assertEquals(listOf(dogTag), pools.find(dogs)!!.defaultTagIds)
        assertEquals(setOf("Perros – mamá", "Viaje"), pools.activeNames().toSet())
        assertEquals(11_000L, pools.pendingMinor(dogs))
        assertEquals(0L, pools.pendingMinor(trip))
    }

    @Test
    fun pendingPartsAreOrderedInputsForTheAllocatorAndSkipVoidedOnes() = runBlocking {
        val all = pools.pendingParts(mom, null)

        assertEquals(listOf(PartId("p2"), PartId("p4")), all.map { it.partId })
        assertEquals(listOf(5_000L, 6_000L), all.map { it.pending.amountMinor })
        assertEquals(LocalDate.of(2026, 10, 12), all.first().occurredOn)
        assertEquals(all, pools.pendingParts(mom, dogs))
        assertEquals(emptyList<Any>(), pools.pendingParts(mom, trip))
        assertEquals(emptyList<Any>(), pools.pendingParts(pepe, null))
    }

    @Test
    fun pendingLinesCarryTheNamesShownWhenRegisteringAReimbursement() = runBlocking {
        val lines = pools.pendingPartLines(mom, null)

        assertEquals(listOf(PartId("p2"), PartId("p4")), lines.map { it.partId })
        assertEquals(listOf("Perros – mamá", "Perros – mamá"), lines.map { it.poolName })
        assertEquals(listOf("Mascotas", "Mascotas"), lines.map { it.categoryName })
        assertEquals(listOf(emptyList<String>(), listOf("Perros")), lines.map { it.tagNames })
        assertEquals(listOf(5_000L, 6_000L), lines.map { it.pending.amountMinor })
        assertEquals(lines.map { it.toPendingPart() }, pools.pendingParts(mom, null))
        assertEquals(lines, pools.pendingPartLines(mom, dogs))
        assertEquals(emptyList<Any>(), pools.pendingPartLines(mom, trip))
    }

    @Test
    fun reimbursedPartsOfAMovement() = runBlocking {
        val parts = pools.reimbursedParts(MovementId("m2"))

        assertEquals(listOf(PartId("p2")), parts.map { it.partId })
        assertEquals(dogs, parts.single().poolId)
        assertEquals(3_000L, parts.single().reimbursed.amountMinor)
        assertEquals(emptyList<Any>(), pools.reimbursedParts(MovementId("m4")))
    }

    @Test
    fun currentPeriodPartsAndWhatWasLeftBefore() = runBlocking {
        val current = pools.currentPeriod(dogs)!!
        assertEquals(PoolPeriodId("dogs-p2"), current.id)
        assertEquals(PoolPeriodStatus.OPEN, current.status)

        val parts = pools.periodParts(current.id)
        assertEquals(listOf(PartId("p4")), parts.map { it.partId })
        assertEquals("Mascotas", parts.single().categoryName)
        assertEquals(listOf("Perros"), parts.single().tagNames)
        assertEquals(6_000L, parts.single().pending.amountMinor)
        assertEquals(5_000L, pools.pendingBefore(current.id))
        assertNull(pools.currentPeriod(PoolId("ghost")))
    }

    @Test
    fun receivablesTotalAndPerPersonBalance() = runBlocking {
        val result = receivables.observeReceivables().first()

        assertEquals(14_000L, result.total.amountMinor)
        val byPerson = result.people.associateBy { it.alias }
        assertEquals(11_000L, byPerson.getValue("Mamá").total.amountMinor)
        assertEquals(3_000L, byPerson.getValue("Pepe").total.amountMinor)
        assertEquals(0L, byPerson.getValue("Pepe").poolsPending.amountMinor)
        assertEquals(3_000L, byPerson.getValue("Pepe").loansPending.amountMinor)
    }

    @Test
    fun receivablesListPoolsWithPendingAndOpenLoans() = runBlocking {
        val result = receivables.observeReceivables().first()

        val pool = result.pools.single()
        assertEquals(dogs, pool.poolId)
        assertEquals(6_000L, pool.periodPending.amountMinor)
        assertEquals(11_000L, pool.totalPending.amountMinor)
        assertEquals("Mamá", pool.responsibleAlias)
        val loan = result.loans.single()
        assertEquals(3_000L, loan.pending.amountMinor)
        assertEquals("Pepe", loan.alias)
    }

    @Test
    fun poolDetailListsPeriodsNewestFirst() = runBlocking {
        val detail = receivables.observePoolDetail(dogs).first()!!

        assertEquals(listOf(PoolPeriodId("dogs-p2"), PoolPeriodId("dogs-p1")), detail.periods.map { it.id })
        assertEquals(PoolPeriodId("dogs-p2"), detail.currentPeriod!!.id)
        val first = detail.periods.last()
        assertEquals(PoolPeriodStatus.STATEMENT_SENT, first.status)
        assertEquals(20_000L, first.total.amountMinor)
        assertEquals(15_000L, first.reimbursed.amountMinor)
        assertEquals(11_000L, detail.pendingTotal.amountMinor)
        assertEquals("Mamá", detail.responsibleAlias)
        assertNull(receivables.observePoolDetail(PoolId("ghost")).first())
    }

    @Test
    fun personDetailCollectsBalancePoolsReimbursementsAndExpenses() = runBlocking {
        val detail = people.observePersonDetail(mom).first()!!

        assertEquals("Mamá", detail.person.alias)
        assertEquals(11_000L, detail.balance.amountMinor)
        assertEquals(listOf(dogs), detail.pools.map { it.poolId })
        assertEquals(emptyList<Any>(), detail.loans)
        val reimbursement = detail.reimbursements.single()
        assertEquals(15_000L, reimbursement.amount.amountMinor)
        assertEquals(false, reimbursement.isVoided)
        assertEquals(listOf(PartId("p4"), PartId("p2"), PartId("p1")), detail.parts.map { it.partId })
        assertEquals(listOf("Perros"), detail.parts.first().tagNames)
        assertEquals("Perros – mamá", detail.parts.first().poolName)

        val pepeDetail = people.observePersonDetail(pepe).first()!!
        assertEquals(3_000L, pepeDetail.balance.amountMinor)
        assertEquals(listOf(LoanId("l1")), pepeDetail.loans.map { it.loan.loanId })
        assertNull(people.observePersonDetail(PersonId("ghost")).first())
    }

    @Test
    fun personBalanceCountsActivePoolsAndOpenLoans() = runBlocking {
        assertEquals(11_000L, people.balanceMinor(mom))
        assertEquals(3_000L, people.balanceMinor(pepe))
        assertEquals(0L, people.balanceMinor(PersonId("ghost")))
    }

    @Test
    fun loanDetailListsItsCurrentRepayments() = runBlocking {
        val detail = loans.observeDetail(LoanId("l1")).first()!!

        assertEquals("Pepe", detail.personName)
        assertEquals(3_000L, detail.loan.pending.amountMinor)
        assertEquals(listOf(MovementId("mrp1")), detail.repayments.map { it.movementId })
        assertEquals(2_000L, detail.repayments.single().amount.amountMinor)
        assertNull(loans.observeDetail(LoanId("ghost")).first())
    }

    @Test
    fun periodsOpenedTheSameDayAreOrderedByInsertionNotByIdText() = runBlocking {
        // "ten-p10" < "ten-p9" como texto: el orden tiene que seguir el de apertura.
        (1..11).forEach { n ->
            db.poolDao().upsertPeriod(
                PoolPeriodEntity("ten-p$n", "ten", "2026-11-10", if (n < 11) "2026-11-10" else null, PoolPeriodStatus.OPEN, null, 0, 0),
            )
        }

        val detail = db.poolQueryDao().observePeriods("ten").first()

        assertEquals((11 downTo 1).map { "ten-p$it" }, detail.map { it.id })
        assertEquals("ten-p11", db.poolQueryDao().currentPeriod("ten")!!.id)
        assertEquals("ten-p11", db.poolDao().openPeriodOf("ten")!!.id)
    }

    @Test
    fun loansAreFoundByIdAndByMovement() = runBlocking {
        val loan = loans.find(LoanId("l1"))!!

        assertEquals(3_000L, loan.pending.amountMinor)
        assertEquals(MovementId("ml1"), loan.movementId)
        assertEquals(loan, loans.findByMovement(MovementId("ml1")))
        assertNull(loans.find(LoanId("ghost")))
    }
}
