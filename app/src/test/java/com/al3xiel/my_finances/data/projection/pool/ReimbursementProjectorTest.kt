package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementPartEntity
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.ReimbursementId
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AllocationData
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.ReimbursementReceived
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import com.al3xiel.my_finances.feature.pools.domain.rules.ReimbursementStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ReimbursementProjectorTest {
    private val movementDao = FakeMovementDao()
    private val ledgerDao = FakeLedgerDao()
    private val poolDao = FakePoolDao(movementDao.parts)
    private val dao = FakeReimbursementDao(movementDao, ledgerDao)
    private val projector = ReimbursementProjector(dao, LedgerPoster(movementDao, ledgerDao), PoolPeriodRefresher(poolDao))

    private val bbva = AccountId("bbva")
    private val mom = PersonId("mom")
    private val day = LocalDate.of(2026, 10, 20)
    private var sequence = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun apply(event: DomainEvent, on: LocalDate = day) {
        sequence++
        projector.apply(
            StoredEvent(
                sequence = sequence, eventId = "e$sequence", schemaVersion = 1, correlationId = "c",
                payload = EventCodec.encode(event), occurredOn = on, recordedAt = Instant.ofEpochMilli(sequence),
                appVersion = "test", previousHash = "p", hash = "h", event = event,
            ),
        )
    }

    /** Lo que ya dejaron el proyector de movimientos y el de cúmulos: la parte, con su cúmulo y su periodo. */
    private fun part(id: String, minor: Long, period: String = "per1") {
        movementDao.parts[id] = MovementPartEntity(id, "m-$id", minor, "pets", PartKind.NORMAL, poolId = "dogs", poolPeriodId = period)
    }

    private suspend fun period() {
        poolDao.upsertPeriod(PoolPeriodEntity("per1", "dogs", "2026-10-01", null, PoolPeriodStatus.OPEN, null, 0, 0))
    }

    private fun received(id: String, minor: Long, vararg allocations: Pair<String, Long>) = ReimbursementReceived(
        ReimbursementId("r$id"), MovementId("mr$id"), mom, bbva, pen(minor), AllocationStrategy.OLDEST_FIRST,
        allocations.map { (part, amount) -> AllocationData(PartId(part), pen(amount)) }, null,
    )

    private suspend fun balance() = ledgerDao.balanceOf(bbva.value)

    @Test
    fun receivedReimbursementBecomesAMovementAndALedgerEntryThatIsNotIncome() = runTest {
        period(); part("p1", 12_000)
        apply(received("1", 12_000, "p1" to 12_000))

        val movement = movementDao.movements.getValue("mr1")
        assertEquals(MovementType.REIMBURSEMENT, movement.type)
        assertEquals(MovementStatus.ACTIVE, movement.status)
        assertEquals("mom", movement.personId)
        assertEquals(12_000L, movement.amountMinor)
        assertEquals(SystemCatalog.incomeSources.single { it.name == "Reembolso" }.id.value, movement.incomeSourceId)
        val entry = ledgerDao.entries.values.single()
        assertEquals(LedgerEntryKind.REIMBURSEMENT_IN, entry.entryKind)
        assertEquals(12_000L, entry.deltaMinor)
        assertEquals("mr1", entry.movementId)
        assertEquals(12_000L, balance())
        assertEquals(12_000L, dao.reimbursements.getValue("r1").amountMinor)
        assertEquals(1, dao.allocations.size)
    }

    @Test
    fun allocationsSettleThePartsFullyOrPartially() = runTest {
        period(); part("p1", 12_000); part("p2", 10_000)
        apply(received("1", 12_040, "p1" to 12_000, "p2" to 40))

        assertEquals(ReimbursementStatus.REIMBURSED, movementDao.parts.getValue("p1").reimbursementStatus)
        assertEquals(12_000L, movementDao.parts.getValue("p1").reimbursedMinor)
        assertEquals(ReimbursementStatus.PARTIALLY_REIMBURSED, movementDao.parts.getValue("p2").reimbursementStatus)
        assertEquals(40L, movementDao.parts.getValue("p2").reimbursedMinor)
    }

    @Test
    fun aSecondReimbursementAddsToThePartAndTheClosingOneSettlesIt() = runTest {
        period(); part("p1", 1_000)
        apply(received("1", 400, "p1" to 400))
        apply(received("2", 600, "p1" to 600))

        assertEquals(1_000L, movementDao.parts.getValue("p1").reimbursedMinor)
        assertEquals(ReimbursementStatus.REIMBURSED, movementDao.parts.getValue("p1").reimbursementStatus)
    }

    @Test
    fun theReimbursedTotalOfThePeriodFollowsTheAllocations() = runTest {
        period(); part("p1", 1_000); part("p2", 500)
        apply(received("1", 700, "p1" to 700))

        val period = poolDao.periods.getValue("per1")
        assertEquals(1_500L, period.totalMinor)
        assertEquals(700L, period.reimbursedMinor)
    }

    @Test
    fun voidingTheReimbursementRevertsMovementEntryAllocationsAndParts() = runTest {
        period(); part("p1", 1_000)
        apply(received("1", 1_000, "p1" to 1_000))
        assertEquals(PoolPeriodStatus.OPEN, poolDao.periods.getValue("per1").status)

        apply(MovementVoided(MovementId("mr1"), "error", null))

        assertEquals(MovementStatus.VOIDED, movementDao.movements.getValue("mr1").status)
        assertTrue(ledgerDao.entries.values.single().isVoided)
        assertEquals(0L, balance())
        assertTrue(dao.allocations.isEmpty())
        assertEquals(0L, movementDao.parts.getValue("p1").reimbursedMinor)
        assertEquals(ReimbursementStatus.PENDING, movementDao.parts.getValue("p1").reimbursementStatus)
        assertEquals(0L, poolDao.periods.getValue("per1").reimbursedMinor)
    }

    @Test
    fun correctingTheAmountOfAReimbursedPartRecomputesItsStatusAndThePeriod() = runTest {
        period(); part("p1", 1_000)
        apply(received("1", 1_000, "p1" to 1_000))
        assertEquals(ReimbursementStatus.REIMBURSED, movementDao.parts.getValue("p1").reimbursementStatus)

        // El proyector de movimientos ya actualizó el monto de la parte cuando llega este evento.
        movementDao.parts["p1"] = movementDao.parts.getValue("p1").copy(amountMinor = 1_500)
        apply(
            com.al3xiel.my_finances.domain.model.event.MovementCorrected(
                MovementId("m-p1"),
                com.al3xiel.my_finances.domain.model.event.MovementSnapshot.Expense(
                    bbva, pen(1_500),
                    listOf(com.al3xiel.my_finances.domain.model.event.ExpensePartData(PartId("p1"), pen(1_500), com.al3xiel.my_finances.domain.model.CategoryId("pets"), listOf(), com.al3xiel.my_finances.domain.model.PoolId("dogs"), null)),
                    null,
                ),
                "monto mal", null,
            ),
        )

        assertEquals(ReimbursementStatus.PARTIALLY_REIMBURSED, movementDao.parts.getValue("p1").reimbursementStatus)
        assertEquals(1_000L, movementDao.parts.getValue("p1").reimbursedMinor)
        assertEquals(1_500L, poolDao.periods.getValue("per1").totalMinor)
        assertEquals(1_000L, poolDao.periods.getValue("per1").reimbursedMinor)
    }

    @Test
    fun voidingAnotherMovementOrTheSameTwiceChangesNothing() = runTest {
        period(); part("p1", 1_000)
        apply(received("1", 400, "p1" to 400))
        apply(MovementVoided(MovementId("someone-else"), "x", null))
        apply(MovementVoided(MovementId("mr1"), "x", null))
        apply(MovementVoided(MovementId("mr1"), "otra vez", null))

        assertEquals(0L, movementDao.parts.getValue("p1").reimbursedMinor)
        assertEquals(1, ledgerDao.entries.size)
    }

    @Test
    fun resetRemovesOnlyWhatThisProjectorDerived() = runTest {
        period(); part("p1", 1_000)
        movementDao.movements["m-other"] = otherMovement()
        apply(received("1", 400, "p1" to 400))

        projector.reset()

        assertTrue(dao.reimbursements.isEmpty() && dao.allocations.isEmpty())
        assertEquals(setOf("m-other"), movementDao.movements.keys)
        assertEquals(0L, movementDao.parts.getValue("p1").reimbursedMinor)
        assertTrue(ledgerDao.entries.isEmpty())
    }

    private fun otherMovement() = com.al3xiel.my_finances.data.projection.movement.MovementEntity(
        "m-other", MovementType.EXPENSE, bbva.value, null, 100, CurrencyCode.PEN, null, null, null,
        "2026-10-01", MovementStatus.ACTIVE, 1, 1,
    )
}
