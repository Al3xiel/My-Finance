package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.ReimbursementId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AllocationData
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.model.event.ReimbursementReceived
import com.al3xiel.my_finances.domain.model.event.SettlementStatementGenerated
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.pools.domain.rules.ReimbursementStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Mismos eventos, mismas tablas: aplicados evento a evento (en vivo) y reconstruidos proyector por proyector (replay),
 * con los tres proyectores que comparten `movement_parts`. Es la prueba del orden entre proyectores.
 */
class ReimbursementReplayTest {
    private val movementDao = FakeMovementDao()
    private val ledgerDao = FakeLedgerDao()
    private val poolDao = FakePoolDao(movementDao.parts)
    private val reimbursementDao = FakeReimbursementDao(movementDao, ledgerDao)

    // El conjunto llega desordenado a propósito: el orden lo pone `order`.
    private val projectors: List<EventProjector> = listOf(
        ReimbursementProjector(reimbursementDao, LedgerPoster(movementDao, ledgerDao), PoolPeriodRefresher(poolDao)),
        PoolProjector(poolDao, PoolPeriodRefresher(poolDao)),
        MovementProjector(movementDao, ledgerDao),
    )

    private val bbva = AccountId("bbva")
    private val mom = PersonId("mom")
    private val day = LocalDate.of(2026, 10, 20)

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun part(id: String, minor: Long) =
        ExpensePartData(PartId(id), pen(minor), CategoryId("pets"), listOf(), PoolId("dogs"), null)

    private fun stored(sequence: Long, event: DomainEvent, on: LocalDate = day) = StoredEvent(
        sequence = sequence, eventId = "e$sequence", schemaVersion = 1, correlationId = "c",
        payload = EventCodec.encode(event), occurredOn = on, recordedAt = Instant.ofEpochMilli(sequence),
        appVersion = "test", previousHash = "p", hash = "h", event = event,
    )

    private val events: List<StoredEvent> = listOf<DomainEvent>(
        AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(100_000)),
        ReimbursementPoolCreated(PoolId("dogs"), "Perros – mamá", mom, PoolKind.PERMANENT, CategoryId("pets"), listOf()),
        ExpenseRegistered(MovementId("m1"), bbva, pen(12_000), listOf(part("p1", 12_000)), null, null),
        ExpenseRegistered(MovementId("m2"), bbva, pen(10_000), listOf(part("p2", 10_000)), null, null),
        ReimbursementReceived(
            ReimbursementId("r1"), MovementId("mr1"), mom, bbva, pen(12_000), AllocationStrategy.OLDEST_FIRST,
            listOf(AllocationData(PartId("p1"), pen(12_000))), null,
        ),
        ReimbursementReceived(
            ReimbursementId("r2"), MovementId("mr2"), mom, bbva, pen(4_000), AllocationStrategy.OLDEST_FIRST,
            listOf(AllocationData(PartId("p2"), pen(4_000))), null,
        ),
        MovementVoided(MovementId("mr2"), "monto mal", null),
        SettlementStatementGenerated(StatementId("s1"), PoolId("dogs"), PoolPeriodId("dogs-p1"), listOf(PartId("p2")), pen(10_000), StatementFormat.PDF),
        MovementCorrected(
            MovementId("m2"),
            MovementSnapshot.Expense(bbva, pen(11_000), listOf(part("p2", 11_000)), null),
            "monto mal", null,
        ),
        ReimbursementReceived(
            ReimbursementId("r3"), MovementId("mr3"), mom, bbva, pen(1_000), AllocationStrategy.MANUAL,
            listOf(AllocationData(PartId("p2"), pen(1_000))), null,
        ),
    ).mapIndexed { i, e -> stored(i + 1L, e, day.plusDays(i.toLong())) }

    private fun snapshot(): List<Any> = listOf(
        movementDao.movements.toMap(), movementDao.parts.toMap(), movementDao.partTags.toSet(),
        ledgerDao.entries.toMap(), ledgerDao.balances.toMap(),
        poolDao.pools.toMap(), poolDao.periods.toMap(), poolDao.statements.toMap(),
        reimbursementDao.reimbursements.toMap(), reimbursementDao.allocations.toSet(),
    )

    @Test
    fun liveAndReplayLeaveTheSameTables() = runTest {
        val ordered = projectors.sortedBy { it.order }
        events.forEach { event -> ordered.forEach { it.apply(event) } }
        val live = snapshot()

        ordered.forEach { projector ->
            projector.reset()
            events.forEach { projector.apply(it) }
        }

        assertEquals(live, snapshot())
    }

    @Test
    fun finalStateMatchesTheStory() = runTest {
        val ordered = projectors.sortedBy { it.order }
        events.forEach { event -> ordered.forEach { it.apply(event) } }

        assertEquals(ReimbursementStatus.REIMBURSED, movementDao.parts.getValue("p1").reimbursementStatus)
        assertEquals(1_000L, movementDao.parts.getValue("p2").reimbursedMinor)
        assertEquals(ReimbursementStatus.PARTIALLY_REIMBURSED, movementDao.parts.getValue("p2").reimbursementStatus)
        // 100.000 − 12.000 − 11.000 + 12.000 + 1.000; el reembolso anulado no suma.
        assertEquals(90_000L, ledgerDao.balances.getValue("bbva").balanceMinor)
        assertEquals(13_000L, poolDao.periods.getValue("dogs-p1").reimbursedMinor)
    }

    /** Aplica los eventos en vivo y luego los reconstruye por pasadas: las tablas tienen que quedar idénticas. */
    private suspend fun assertLiveEqualsReplay(script: List<DomainEvent>) {
        val sequenced = script.mapIndexed { i, e -> stored(i + 1L, e, day.plusDays(i.toLong())) }
        val ordered = projectors.sortedBy { it.order }
        sequenced.forEach { event -> ordered.forEach { it.apply(event) } }
        val live = snapshot()

        ordered.forEach { projector ->
            projector.reset()
            sequenced.forEach { projector.apply(it) }
        }

        assertEquals(live, snapshot())
    }

    private val open = AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(100_000))
    private val dogs = ReimbursementPoolCreated(PoolId("dogs"), "Perros – mamá", mom, PoolKind.PERMANENT, CategoryId("pets"), listOf())
    private fun reimburse(id: String, part: String, minor: Long) = ReimbursementReceived(
        ReimbursementId("r$id"), MovementId("mr$id"), mom, bbva, pen(minor), AllocationStrategy.OLDEST_FIRST,
        listOf(AllocationData(PartId(part), pen(minor))), null,
    )

    @Test
    fun correctingAFullyReimbursedPartUpwardsLeavesItPartiallyReimbursedInLiveAndReplay() = runTest {
        val script = listOf<DomainEvent>(
            open, dogs,
            ExpenseRegistered(MovementId("m1"), bbva, pen(12_000), listOf(part("p1", 12_000)), null, null),
            reimburse("1", "p1", 12_000),
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(bbva, pen(15_000), listOf(part("p1", 15_000)), null),
                "monto mal", null,
            ),
        )

        assertLiveEqualsReplay(script)

        val p1 = movementDao.parts.getValue("p1")
        assertEquals(ReimbursementStatus.PARTIALLY_REIMBURSED, p1.reimbursementStatus)
        assertEquals(12_000L, p1.reimbursedMinor)
        assertEquals(3_000L, poolDao.periods.getValue("dogs-p1").let { it.totalMinor - it.reimbursedMinor })
    }

    @Test
    fun correctingAPartiallyReimbursedPartDownToWhatWasReimbursedSettlesItInLiveAndReplay() = runTest {
        val script = listOf<DomainEvent>(
            open, dogs,
            ExpenseRegistered(MovementId("m1"), bbva, pen(10_000), listOf(part("p1", 10_000)), null, null),
            reimburse("1", "p1", 4_000),
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(bbva, pen(4_000), listOf(part("p1", 4_000)), null),
                "monto mal", null,
            ),
        )

        assertLiveEqualsReplay(script)

        assertEquals(ReimbursementStatus.REIMBURSED, movementDao.parts.getValue("p1").reimbursementStatus)
    }

    @Test
    fun voidingAnExpenseThatSitsInAClosedPeriodAgreesLiveAndReplay() = runTest {
        val script = listOf<DomainEvent>(
            open, dogs,
            ExpenseRegistered(MovementId("m1"), bbva, pen(12_000), listOf(part("p1", 12_000)), null, null),
            ExpenseRegistered(MovementId("m2"), bbva, pen(8_000), listOf(part("p2", 8_000)), null, null),
            SettlementStatementGenerated(StatementId("s1"), PoolId("dogs"), PoolPeriodId("dogs-p1"), listOf(PartId("p1"), PartId("p2")), pen(20_000), StatementFormat.PDF),
            MovementVoided(MovementId("m1"), "duplicado", null),
        )

        assertLiveEqualsReplay(script)

        assertEquals(8_000L, poolDao.periods.getValue("dogs-p1").totalMinor)
        assertEquals(null, movementDao.parts.getValue("p1").poolPeriodId)
    }

    @Test
    fun anEventPoolKeepsItsSinglePeriodAcrossAStatementInLiveAndReplay() = runTest {
        val trip = ReimbursementPoolCreated(PoolId("trip"), "Viaje", mom, PoolKind.EVENT, null, listOf())
        fun tripPart(id: String, minor: Long) = ExpensePartData(PartId(id), pen(minor), CategoryId("pets"), listOf(), PoolId("trip"), null)
        val script = listOf<DomainEvent>(
            open, trip,
            ExpenseRegistered(MovementId("m1"), bbva, pen(5_000), listOf(tripPart("t1", 5_000)), null, null),
            SettlementStatementGenerated(StatementId("s1"), PoolId("trip"), PoolPeriodId("trip-p1"), listOf(PartId("t1")), pen(5_000), StatementFormat.IMAGE),
            ExpenseRegistered(MovementId("m2"), bbva, pen(2_000), listOf(tripPart("t2", 2_000)), null, null),
            reimburse("1", "t1", 5_000),
        )

        assertLiveEqualsReplay(script)

        assertEquals(1, poolDao.periods.size)
        assertEquals(2_000L, poolDao.periods.getValue("trip-p1").let { it.totalMinor - it.reimbursedMinor })
    }
}
