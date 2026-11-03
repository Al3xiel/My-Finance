package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.movement.MovementPartEntity
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolArchived
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolRenamed
import com.al3xiel.my_finances.domain.model.event.SettlementStatementGenerated
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PoolProjectorTest {
    private val dao = FakePoolDao()
    private val projector = PoolProjector(dao, PoolPeriodRefresher(dao))

    private val day = LocalDate.of(2026, 10, 1)
    private val mom = PersonId("mom")
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

    private fun create(id: String, kind: PoolKind = PoolKind.PERMANENT) =
        ReimbursementPoolCreated(PoolId(id), id, mom, kind, CategoryId("pets"), listOf())

    private fun partData(id: String, minor: Long, pool: String?) =
        ExpensePartData(PartId(id), pen(minor), CategoryId("pets"), listOf(), pool?.let(::PoolId), null)

    /** Lo que ya dejó el proyector de movimientos: la parte existe con su monto y su cúmulo. */
    private fun seed(partId: String, movementId: String, minor: Long, pool: String?) {
        dao.parts[partId] = MovementPartEntity(partId, movementId, minor, "pets", PartKind.NORMAL, poolId = pool)
    }

    private suspend fun spend(movement: String, on: LocalDate, vararg parts: Triple<String, Long, String?>) {
        parts.forEach { (id, minor, pool) -> seed(id, movement, minor, pool) }
        apply(
            ExpenseRegistered(
                MovementId(movement), AccountId("a"), pen(parts.sumOf { it.second }),
                parts.map { (id, minor, pool) -> partData(id, minor, pool) }, null, null,
            ),
            on,
        )
    }

    private fun statement(id: String, pool: String, period: String, pending: Long = 0) =
        SettlementStatementGenerated(StatementId(id), PoolId(pool), PoolPeriodId(period), listOf(), pen(pending), StatementFormat.PDF)

    @Test
    fun creatingAPoolOpensItsFirstPeriod() = runTest {
        apply(create("dogs"))

        val pool = dao.pools.getValue("dogs")
        assertEquals("mom", pool.responsiblePersonId)
        assertEquals(PoolKind.PERMANENT, pool.kind)
        assertEquals("pets", pool.defaultCategoryId)
        val period = dao.periods.values.single()
        assertEquals("dogs", period.poolId)
        assertEquals(PoolPeriodStatus.OPEN, period.status)
        assertEquals("2026-10-01", period.openedOn)
        assertNull(period.closedOn)
        assertEquals(0L, period.totalMinor)
    }

    @Test
    fun aPoolPartLandsInTheOpenPeriodAndAddsToItsTotal() = runTest {
        apply(create("dogs"))
        spend("m1", day, Triple("p1", 12_000L, "dogs"), Triple("p2", 500L, null))

        val period = dao.periods.values.single()
        assertEquals(period.id, dao.parts.getValue("p1").poolPeriodId)
        assertNull(dao.parts.getValue("p2").poolPeriodId)
        assertEquals(12_000L, period.totalMinor)
    }

    @Test
    fun aPartForAnUnknownPoolIsLeftAlone() = runTest {
        spend("m1", day, Triple("p1", 100L, "ghost"))
        assertNull(dao.parts.getValue("p1").poolPeriodId)
    }

    @Test
    fun statementOnAPermanentPoolClosesThePeriodAndOpensTheNext() = runTest {
        apply(create("dogs"))
        spend("m1", day, Triple("p1", 12_000L, "dogs"))
        val first = dao.periods.values.single().id

        apply(statement("s1", "dogs", first, pending = 12_000), LocalDate.of(2026, 10, 31))

        val closed = dao.periods.getValue(first)
        assertEquals(PoolPeriodStatus.STATEMENT_SENT, closed.status)
        assertEquals("s1", closed.statementId)
        assertEquals("2026-10-31", closed.closedOn)
        assertEquals(12_000L, dao.statements.getValue("s1").pendingTotalMinor)
        assertEquals("2026-10-31", dao.statements.getValue("s1").generatedOn)
        val next = dao.periods.values.single { it.id != first }
        assertEquals(PoolPeriodStatus.OPEN, next.status)
        assertEquals("2026-10-31", next.openedOn)

        spend("m2", LocalDate.of(2026, 11, 2), Triple("p3", 300L, "dogs"))
        assertEquals(next.id, dao.parts.getValue("p3").poolPeriodId)
        assertEquals(12_000L, dao.periods.getValue(first).totalMinor)
        assertEquals(300L, dao.periods.getValue(next.id).totalMinor)
    }

    @Test
    fun aSecondStatementOnAnAlreadyClosedPermanentPeriodIsIgnored() = runTest {
        apply(create("dogs"))
        spend("m1", day, Triple("p1", 12_000L, "dogs"))
        val first = dao.periods.values.single().id
        apply(statement("s1", "dogs", first, pending = 12_000), LocalDate.of(2026, 10, 31))

        apply(statement("s2", "dogs", first, pending = 12_000), LocalDate.of(2026, 10, 31))

        assertEquals(2, dao.periods.size)
        assertEquals("s1", dao.periods.getValue(first).statementId)
        assertEquals(setOf("s1"), dao.statements.keys)
    }

    @Test
    fun anExpenseDatedBeforeTheLastCloseFallsInTheCurrentOpenPeriod() = runTest {
        apply(create("dogs"))
        val first = dao.periods.values.single().id
        apply(statement("s1", "dogs", first), LocalDate.of(2026, 10, 31))
        val next = dao.periods.values.single { it.id != first }.id

        spend("m1", LocalDate.of(2026, 10, 5), Triple("late", 700L, "dogs"))

        assertEquals(next, dao.parts.getValue("late").poolPeriodId)
    }

    @Test
    fun statementOnAnEventPoolKeepsASinglePeriodThatIsSettledOnlyWhenNothingIsPending() = runTest {
        apply(create("trip", PoolKind.EVENT))
        spend("m1", day, Triple("p1", 1_000L, "trip"))
        val only = dao.periods.values.single().id

        apply(statement("s1", "trip", only), LocalDate.of(2026, 10, 10))
        assertEquals(1, dao.periods.size)
        assertEquals(PoolPeriodStatus.STATEMENT_SENT, dao.periods.getValue(only).status)
        assertNull(dao.periods.getValue(only).closedOn)

        dao.parts["p1"] = dao.parts.getValue("p1").copy(reimbursedMinor = 1_000)
        PoolPeriodRefresher(dao).refresh()
        assertEquals(PoolPeriodStatus.SETTLED, dao.periods.getValue(only).status)

        spend("m2", LocalDate.of(2026, 10, 12), Triple("p2", 200L, "trip"))
        assertEquals(PoolPeriodStatus.STATEMENT_SENT, dao.periods.getValue(only).status)
    }

    @Test
    fun voidingTheMovementTakesItsPartsOutOfThePeriod() = runTest {
        apply(create("dogs"))
        spend("m1", day, Triple("p1", 12_000L, "dogs"))
        spend("m2", day, Triple("p2", 500L, "dogs"))

        apply(MovementVoided(MovementId("m1"), "duplicado", null))

        assertNull(dao.parts.getValue("p1").poolPeriodId)
        assertEquals(500L, dao.periods.values.single().totalMinor)
    }

    @Test
    fun correctionKeepsTheClosedPeriodOfAPartThatStaysAndMovesTheOthers() = runTest {
        apply(create("dogs"))
        apply(create("cats"))
        spend("m1", day, Triple("p1", 400L, "dogs"), Triple("p2", 100L, "dogs"), Triple("p3", 50L, null))
        val dogsFirst = dao.periods.values.single { it.poolId == "dogs" }.id
        apply(statement("s1", "dogs", dogsFirst), LocalDate.of(2026, 10, 31))
        val dogsNext = dao.periods.values.single { it.poolId == "dogs" && it.id != dogsFirst }.id

        // El monto de p1 cambia, p2 pasa a "cats", p3 entra a "dogs" y p4 es nueva en "dogs".
        seed("p1", "m1", 500, "dogs")
        seed("p2", "m1", 100, "cats")
        seed("p3", "m1", 50, "dogs")
        seed("p4", "m1", 70, "dogs")
        dao.parts["p1"] = dao.parts.getValue("p1").copy(poolPeriodId = dogsFirst)
        dao.parts["p2"] = dao.parts.getValue("p2").copy(poolPeriodId = dogsFirst)
        apply(
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(
                    AccountId("a"), pen(720),
                    listOf(partData("p1", 500, "dogs"), partData("p2", 100, "cats"), partData("p3", 50, "dogs"), partData("p4", 70, "dogs")),
                    null,
                ),
                "reasignado", null,
            ),
            LocalDate.of(2026, 11, 3),
        )

        assertEquals(dogsFirst, dao.parts.getValue("p1").poolPeriodId)
        assertEquals(dao.periods.values.single { it.poolId == "cats" }.id, dao.parts.getValue("p2").poolPeriodId)
        assertEquals(dogsNext, dao.parts.getValue("p3").poolPeriodId)
        assertEquals(dogsNext, dao.parts.getValue("p4").poolPeriodId)
        assertEquals(500L, dao.periods.getValue(dogsFirst).totalMinor)
        assertEquals(120L, dao.periods.getValue(dogsNext).totalMinor)
    }

    @Test
    fun correctionThatDropsThePoolFromAPartClearsItsPeriod() = runTest {
        apply(create("dogs"))
        spend("m1", day, Triple("p1", 400L, "dogs"))
        seed("p1", "m1", 400, null)
        dao.parts["p1"] = dao.parts.getValue("p1").copy(poolPeriodId = dao.periods.values.single().id)

        apply(
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(AccountId("a"), pen(400), listOf(partData("p1", 400, null)), null),
                "sin cúmulo", null,
            ),
        )

        assertNull(dao.parts.getValue("p1").poolPeriodId)
        assertEquals(0L, dao.periods.values.single().totalMinor)
    }

    @Test
    fun renameAndArchiveUpdateThePool() = runTest {
        apply(create("dogs"))
        apply(ReimbursementPoolRenamed(PoolId("dogs"), "Perros"))
        apply(ReimbursementPoolArchived(PoolId("dogs")))

        val pool = dao.pools.getValue("dogs")
        assertEquals("Perros", pool.name)
        assertEquals(true, pool.isArchived)
    }

    @Test
    fun replayProducesTheSameProjection() = runTest {
        suspend fun script() {
            sequence = 0
            apply(create("dogs"))
            spend("m1", day, Triple("p1", 12_000L, "dogs"))
            apply(statement("s1", "dogs", "dogs-p1"), LocalDate.of(2026, 10, 31))
            spend("m2", LocalDate.of(2026, 11, 2), Triple("p2", 300L, "dogs"))
            apply(MovementVoided(MovementId("m2"), "error", null))
        }

        script()
        val first = listOf(dao.pools.toMap(), dao.periods.toMap(), dao.statements.toMap(), dao.parts.toMap())

        projector.reset()
        script()

        assertEquals(first, listOf(dao.pools.toMap(), dao.periods.toMap(), dao.statements.toMap(), dao.parts.toMap()))
    }
}
