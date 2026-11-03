package com.al3xiel.my_finances.feature.funds.domain.usecase

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.FundCreated
import com.al3xiel.my_finances.domain.model.event.FundEdited
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.UsageDayMarked
import com.al3xiel.my_finances.domain.model.event.UsageDayUnmarked
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.funds.domain.model.FundEditFacts
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class FundUseCasesTest {
    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC)
    private val recorder = EventRecorder(store, ids, clock)
    private val funds = FakeFundRepository()
    private val lock = MovementWriteLock()

    private val create = CreateFundUseCase(funds, recorder, ids)
    private val edit = EditFundUseCase(funds, recorder, lock)
    private val topUp = TopUpFundUseCase(funds, recorder, lock)
    private val markDay = MarkUsageDayUseCase(funds, recorder, lock)

    private val fare = FundId("fare")
    private val today = LocalDate.of(2026, 10, 27)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun failed(error: BusinessError) = Outcome.Failed(error)

    private fun existing(mode: ConsumptionMode = ConsumptionMode.DAILY_ESTIMATE, status: FundStatus = FundStatus.ACTIVE) =
        funds.put(fare, "Pasajes", mode, 500, oct(1), oct(31), allocated = 5_000, status = status)

    // --- crear ---

    @Test
    fun createsAFundWithItsTerms() = runTest {
        val result = create("  Pasajes ", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(31))

        assertEquals(Outcome.Ok(FundId("id-1")), result)
        assertEquals(
            FundCreated(FundId("id-1"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(31)),
            store.events.single().event,
        )
    }

    @Test
    fun theEstimatedModeNeedsAPositiveDailyEstimateAndTheOthersDropIt() = runTest {
        assertEquals(failed(BusinessError.InvalidAmount), create("A", ConsumptionMode.DAILY_ESTIMATE, null, oct(1), null))
        assertEquals(failed(BusinessError.InvalidAmount), create("A", ConsumptionMode.DAILY_ESTIMATE, pen(0), oct(1), null))
        assertTrue(store.events.isEmpty())

        create("B", ConsumptionMode.DETAILED, pen(500), oct(1), null)
        assertEquals(
            FundCreated(FundId("id-1"), "B", ConsumptionMode.DETAILED, null, oct(1), null),
            store.events.single().event,
        )
    }

    @Test
    fun rejectsABlankOrRepeatedNameAndAnEndBeforeTheStart() = runTest {
        existing()

        assertEquals(failed(BusinessError.BlankName), create("  ", ConsumptionMode.NONE, null, oct(1), null))
        assertEquals(failed(BusinessError.DuplicateName), create(" pasajes ", ConsumptionMode.NONE, null, oct(1), null))
        assertEquals(failed(BusinessError.DateOutOfFundRange), create("Nuevo", ConsumptionMode.NONE, null, oct(10), oct(9)))
        assertTrue(store.events.isEmpty())
    }

    // --- editar ---

    @Test
    fun editsTheFreeFieldsEvenWithActivity() = runTest {
        existing()
        funds.facts[fare] = FundEditFacts(hasUsageDays = true, hasCounts = true, hasAssignedExpenses = true)

        val result = edit(fare, "Transporte", ConsumptionMode.DAILY_ESTIMATE, pen(600), oct(1), oct(15))

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals(
            FundEdited(fare, "Transporte", ConsumptionMode.DAILY_ESTIMATE, pen(600), oct(1), oct(15)),
            store.events.single().event,
        )
    }

    @Test
    fun theModeIsLockedWithUsageDaysOrCountsAndTheStartWithExpenses() = runTest {
        existing()
        funds.facts[fare] = FundEditFacts(hasUsageDays = true, hasCounts = false, hasAssignedExpenses = false)
        assertEquals(failed(BusinessError.FieldLocked), edit(fare, "Pasajes", ConsumptionMode.DETAILED, null, oct(1), oct(31)))

        funds.facts[fare] = FundEditFacts(hasUsageDays = false, hasCounts = false, hasAssignedExpenses = true)
        assertEquals(failed(BusinessError.FieldLocked), edit(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(2), oct(31)))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun editRejectsMissingClosedRepeatedNameAndBadEstimate() = runTest {
        assertEquals(failed(BusinessError.NotFound), edit(fare, "X", ConsumptionMode.NONE, null, oct(1), null))

        existing()
        funds.put(FundId("other"), "Comida")
        assertEquals(failed(BusinessError.DuplicateName), edit(fare, "comida", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), null))
        assertEquals(failed(BusinessError.InvalidAmount), edit(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, null, oct(1), null))
        assertEquals(Outcome.Ok(Unit), edit(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(31)))

        existing(status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), edit(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(31)))
    }

    // --- recargar ---

    @Test
    fun topsUpWithOwnMoneyUpToTheAvailable() = runTest {
        existing()
        funds.available = 2_000

        assertEquals(Outcome.Ok(Unit), topUp(fare, pen(2_000)))
        assertEquals(FundToppedUp(fare, pen(2_000), null), store.events.single().event)
    }

    @Test
    fun topUpRejectsMoreThanAvailableZeroMissingAndClosedFunds() = runTest {
        assertEquals(failed(BusinessError.NotFound), topUp(fare, pen(100)))

        existing()
        funds.available = 1_000
        assertEquals(failed(BusinessError.ExceedsAvailable), topUp(fare, pen(1_001)))
        assertEquals(failed(BusinessError.InvalidAmount), topUp(fare, pen(0)))

        existing(status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), topUp(fare, pen(100)))
        assertTrue(store.events.isEmpty())
    }

    // --- días de uso ---

    @Test
    fun marksAndUnmarksAUsageDay() = runTest {
        existing()
        funds.usage[fare] = setOf(oct(5))

        assertEquals(Outcome.Ok(Unit), markDay(fare, oct(4), marked = true))
        assertEquals(Outcome.Ok(Unit), markDay(fare, oct(5), marked = false))

        assertEquals(listOf(UsageDayMarked(fare, oct(4)), UsageDayUnmarked(fare, oct(5))), store.events.map { it.event })
        assertEquals(oct(4), store.events.first().occurredOn)
    }

    @Test
    fun usageDaysNeedAnActiveEstimatedFundInRangeAndNotInTheFuture() = runTest {
        assertEquals(failed(BusinessError.NotFound), markDay(fare, oct(4), marked = true))

        existing(ConsumptionMode.DETAILED)
        assertEquals(failed(BusinessError.UsageDayNotAllowed), markDay(fare, oct(4), marked = true))

        existing()
        assertEquals(failed(BusinessError.DateOutOfFundRange), markDay(fare, LocalDate.of(2026, 9, 30), marked = true))
        assertEquals(failed(BusinessError.FutureDate), markDay(fare, today.plusDays(1), marked = true))

        existing(status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), markDay(fare, oct(4), marked = true))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun unmarkingADayThatIsNotMarkedIsNotFound() = runTest {
        existing()

        assertEquals(failed(BusinessError.NotFound), markDay(fare, oct(4), marked = false))
    }

    @Test
    fun usageDaysLockTheStartAndTheEndCannotPrecedeTheLastActivity() = runTest {
        existing()
        funds.facts[fare] = FundEditFacts(hasUsageDays = true, hasCounts = false, hasAssignedExpenses = false, latestActivityOn = oct(10))

        assertEquals(failed(BusinessError.FieldLocked), edit(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(2), oct(31)))
        assertEquals(failed(BusinessError.DateOutOfFundRange), edit(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(9)))
        assertEquals(Outcome.Ok(Unit), edit(fare, "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(10)))
    }

    @Test
    fun editingAndMarkingADayWaitForTheSharedLock() = runTest(UnconfinedTestDispatcher()) {
        existing()
        val gate = CompletableDeferred<Unit>()
        val holder = launch { lock.withLock { gate.await() } }

        val edited = async { edit(fare, "Transporte", ConsumptionMode.DAILY_ESTIMATE, pen(500), oct(1), oct(31)) }
        val marked = async { markDay(fare, oct(4), marked = true) }
        assertTrue(edited.isActive && marked.isActive)
        assertTrue(store.events.isEmpty())

        gate.complete(Unit)
        assertEquals(Outcome.Ok(Unit), edited.await())
        assertEquals(Outcome.Ok(Unit), marked.await())
        holder.join()
        assertEquals(2, store.events.size)
    }
}
