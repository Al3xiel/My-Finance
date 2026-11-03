package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.SettlementStatementGenerated
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.model.StatementPart
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse

class GenerateSettlementStatementUseCaseTest {
    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-10-31T15:00:00Z"), ZoneOffset.UTC)
    private val recorder = EventRecorder(store, ids, clock)
    private val pools = FakePoolRepository()
    private val people = FakePersonRepository()
    private val lock = com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock()

    private val preview = PrepareSettlementStatementUseCase(pools, people, recorder)
    private val generate = GenerateSettlementStatementUseCase(pools, people, recorder, ids, lock)

    private val dogs = PoolId("dogs")
    private val period = PoolPeriodId("dogs-p1")
    private val mom = PersonId("mom")
    private val today = LocalDate.of(2026, 10, 31)

    init {
        people.people[mom] = Person(mom, "Mamá", false)
        pools.pools[dogs] = Pool(dogs, "Perros – mamá", mom, PoolKind.PERMANENT, null, emptyList(), false)
        pools.currentPeriods[dogs] = PoolPeriod(period, dogs, LocalDate.of(2026, 10, 1), null, PoolPeriodStatus.OPEN, null, pen(0), pen(0))
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun part(id: String, day: Int, seq: Long, amount: Long, pending: Long, category: String = "Mascotas", vararg tags: String) =
        StatementPart(PartId(id), LocalDate.of(2026, 10, day), seq, category, tags.toList(), pen(amount), pen(pending))

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun theStatementListsOnlyWhatIsPendingInDateOrderWithoutNotesOrBalances() = runTest {
        pools.periodParts[period] = listOf(
            part("p2", 12, 9, 80, 80, "Comida", "Perros"),
            part("p1", 3, 4, 120, 20, "Mascotas"),
            part("paid", 5, 6, 50, 0),
        )

        val result = preview(dogs)

        val content = (result as Outcome.Ok).value
        assertEquals("Perros – mamá", content.poolName)
        assertEquals("Mamá", content.responsibleAlias)
        assertEquals(LocalDate.of(2026, 10, 1), content.periodFrom)
        assertEquals(today, content.generatedOn)
        assertEquals(listOf(3, 12), content.lines.map { it.occurredOn.dayOfMonth })
        assertEquals(listOf("Mascotas", "Comida"), content.lines.map { it.categoryName })
        assertEquals(listOf(emptyList(), listOf("Perros")), content.lines.map { it.tagNames })
        assertEquals(pen(100), content.pendingTotal)
        assertEquals(pen(0), content.previousPending)
        assertEquals(0, store.events.size)
    }

    @Test
    fun pendingFromEarlierStatementsIsShownApartAndNotAddedToTheListedTotal() = runTest {
        pools.periodParts[period] = listOf(part("p1", 3, 4, 120, 120))
        pools.previousPending[period] = 300

        val content = (preview(dogs) as Outcome.Ok).value

        assertEquals(pen(120), content.pendingTotal)
        assertEquals(pen(300), content.previousPending)
        assertEquals(pen(420), content.grandTotal)
    }

    @Test
    fun generatingEmitsTheEventWithThePendingPartsAndTheirTotal() = runTest {
        pools.periodParts[period] = listOf(part("p1", 3, 4, 120, 20), part("p2", 12, 9, 80, 80), part("paid", 5, 6, 50, 0))

        val result = generate(dogs, StatementFormat.PDF)

        val generated = (result as Outcome.Ok).value
        assertEquals(StatementId("id-1"), generated.statementId)
        assertEquals(pen(100), generated.content.pendingTotal)
        val stored = store.events.single()
        assertEquals(
            SettlementStatementGenerated(StatementId("id-1"), dogs, period, listOf(PartId("p1"), PartId("p2")), pen(100), StatementFormat.PDF),
            stored.event,
        )
        assertEquals(today, stored.occurredOn)
    }

    @Test
    fun withNothingPendingThereIsNothingToSettle() = runTest {
        pools.periodParts[period] = listOf(part("paid", 5, 6, 50, 0))

        assertEquals(failed(BusinessError.NothingPending), generate(dogs, StatementFormat.IMAGE))
        assertEquals(failed(BusinessError.NothingPending), preview(dogs))
        assertEquals(0, store.events.size)
    }

    @Test
    fun rejectsUnknownArchivedAndPoolsWithoutAnOpenPeriod() = runTest {
        pools.pools[PoolId("old")] = Pool(PoolId("old"), "Viejo", mom, PoolKind.EVENT, null, emptyList(), true)
        pools.pools[PoolId("empty")] = Pool(PoolId("empty"), "Vacío", mom, PoolKind.EVENT, null, emptyList(), false)

        assertEquals(failed(BusinessError.NotFound), generate(PoolId("ghost"), StatementFormat.PDF))
        assertEquals(failed(BusinessError.AlreadyArchived), generate(PoolId("old"), StatementFormat.PDF))
        assertEquals(failed(BusinessError.NotFound), generate(PoolId("empty"), StatementFormat.PDF))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun waitsForTheWriteLockSoADoubleConfirmationCannotCloseThePeriodTwice() = runTest {
        pools.periodParts[period] = listOf(part("p1", 3, 4, 120, 120))
        val release = CompletableDeferred<Unit>()
        val holder = launch { lock.withLock { release.await() } }
        runCurrent()

        val call = async { generate(dogs, StatementFormat.PDF) }
        runCurrent()
        assertFalse(call.isCompleted)
        assertEquals(0, store.events.size)

        release.complete(Unit)
        assertTrue(call.await() is Outcome.Ok<*>)
        holder.join()
        assertEquals(1, store.events.size)
    }
}
