package com.al3xiel.my_finances.data.projection.fund

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.FundClosed
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.FundCreated
import com.al3xiel.my_finances.domain.model.event.FundEdited
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.UntrackedSpendData
import com.al3xiel.my_finances.domain.model.event.UsageDayMarked
import com.al3xiel.my_finances.domain.model.event.UsageDayUnmarked
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FundProjectorTest {
    private val dao = FakeFundDao()
    private val projector = FundProjector(dao)

    private val pasajes = FundId("pasajes")
    private val comida = FundId("comida")
    private val oct1 = LocalDate.of(2026, 10, 1)
    private var sequence = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun apply(event: DomainEvent, on: LocalDate = oct1) {
        sequence++
        projector.apply(
            StoredEvent(
                sequence = sequence, eventId = "e$sequence", schemaVersion = 1, correlationId = "c",
                payload = EventCodec.encode(event), occurredOn = on, recordedAt = Instant.ofEpochMilli(sequence),
                appVersion = "test", previousHash = "p", hash = "h", event = event,
            ),
        )
    }

    private fun create(id: FundId = pasajes, mode: ConsumptionMode = ConsumptionMode.DAILY_ESTIMATE, estimate: Long? = 500) =
        FundCreated(id, id.value, mode, estimate?.let(::pen), oct1, LocalDate.of(2026, 10, 31))

    @Test
    fun creatingAFundStartsActiveWithNothingAllocated() = runTest {
        apply(create())

        val fund = dao.funds.getValue("pasajes")
        assertEquals(FundStatus.ACTIVE, fund.status)
        assertEquals(ConsumptionMode.DAILY_ESTIMATE, fund.consumptionMode)
        assertEquals(500L, fund.dailyEstimateMinor)
        assertEquals("2026-10-01", fund.startsOn)
        assertEquals("2026-10-31", fund.endsOn)
        assertEquals(0L, fund.allocatedMinor)
        assertEquals(0L, fund.untrackedSpentMinor)
        assertNull(fund.closedOn)
    }

    @Test
    fun editingReplacesTheEditableDataAndKeepsTheMoney() = runTest {
        apply(create())
        apply(FundToppedUp(pasajes, pen(5_000), null))

        apply(FundEdited(pasajes, "Pasajes oct", ConsumptionMode.DAILY_ESTIMATE, pen(600), oct1, null))

        val fund = dao.funds.getValue("pasajes")
        assertEquals("Pasajes oct", fund.name)
        assertEquals(600L, fund.dailyEstimateMinor)
        assertNull(fund.endsOn)
        assertEquals(5_000L, fund.allocatedMinor)
    }

    @Test
    fun topUpsAddToTheAllocationAndAreRecordedWithTheirIncome() = runTest {
        apply(create())
        apply(FundToppedUp(pasajes, pen(5_000), MovementId("income1")))
        apply(FundToppedUp(pasajes, pen(1_000), null), LocalDate.of(2026, 10, 3))

        assertEquals(6_000L, dao.funds.getValue("pasajes").allocatedMinor)
        assertEquals(listOf("2-topup", "3-topup"), dao.topUps.keys.toList())
        assertEquals("income1", dao.topUps.getValue("2-topup").linkedIncomeMovementId)
        assertNull(dao.topUps.getValue("3-topup").linkedIncomeMovementId)
        assertEquals("2026-10-03", dao.topUps.getValue("3-topup").occurredOn)
    }

    @Test
    fun usageDaysAreMarkedOnceAndUnmarked() = runTest {
        apply(create())
        val day = LocalDate.of(2026, 10, 5)

        apply(UsageDayMarked(pasajes, day))
        apply(UsageDayMarked(pasajes, day))
        apply(UsageDayMarked(pasajes, day.plusDays(1)))
        assertEquals(2, dao.usageDayCount("pasajes"))

        apply(UsageDayUnmarked(pasajes, day))
        assertEquals(listOf("2026-10-06"), dao.usageDays.map { it.day })
    }

    @Test
    fun anEstimatedCountRecordsRealSpentEstimateAndDeviation() = runTest {
        apply(create())
        apply(FundToppedUp(pasajes, pen(5_000), null))
        listOf(5, 6, 7).forEach { apply(UsageDayMarked(pasajes, LocalDate.of(2026, 10, it))) }

        apply(FundCounted(pasajes, pen(3_800), null), LocalDate.of(2026, 10, 8))

        val count = dao.counts.values.single()
        assertEquals(3_800L, count.declaredRemainingMinor)
        assertEquals(1_200L, count.realSpentMinor)
        assertEquals(1_500L, count.estimatedSpentMinor)
        assertEquals(-300L, count.deviationMinor)
        assertEquals("2026-10-08", count.occurredOn)
        assertEquals(0L, dao.funds.getValue("pasajes").untrackedSpentMinor)
    }

    @Test
    fun aCountWithUntrackedSpendAddsItToTheFund() = runTest {
        apply(create())
        apply(FundToppedUp(pasajes, pen(5_000), null))

        apply(FundCounted(pasajes, pen(4_000), UntrackedSpendData(MovementId("m9"), AccountId("cash"), CategoryId("transport"), pen(1_000))))
        apply(FundCounted(pasajes, pen(3_500), UntrackedSpendData(MovementId("m10"), AccountId("cash"), CategoryId("transport"), pen(500))))

        assertEquals(1_500L, dao.funds.getValue("pasajes").untrackedSpentMinor)
        assertEquals(listOf(1_000L, 1_500L), dao.counts.values.map { it.realSpentMinor })
    }

    @Test
    fun withoutAnEstimateTheCountHasNoEstimateNorDeviation() = runTest {
        apply(create(mode = ConsumptionMode.DETAILED, estimate = null))
        apply(FundToppedUp(pasajes, pen(5_000), null))

        apply(FundCounted(pasajes, pen(4_000), null))

        val count = dao.counts.values.single()
        assertEquals(1_000L, count.realSpentMinor)
        assertNull(count.estimatedSpentMinor)
        assertNull(count.deviationMinor)
    }

    @Test
    fun closingReleasingKeepsTheMoneyOutOfAnyOtherFund() = runTest {
        apply(create()); apply(create(comida, ConsumptionMode.DETAILED, null))
        apply(FundToppedUp(pasajes, pen(5_000), null))

        apply(FundClosed(pasajes, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null), LocalDate.of(2026, 10, 31))

        val fund = dao.funds.getValue("pasajes")
        assertEquals(FundStatus.CLOSED, fund.status)
        assertEquals(RemainderPolicy.RELEASE_TO_AVAILABLE, fund.remainderPolicy)
        assertEquals("2026-10-31", fund.closedOn)
        assertEquals(0L, dao.funds.getValue("comida").allocatedMinor)
    }

    @Test
    fun closingWithRollOverRaisesTheAllocationOfTheDestination() = runTest {
        apply(create()); apply(create(comida, ConsumptionMode.DETAILED, null))
        apply(FundToppedUp(comida, pen(2_000), null))

        apply(FundClosed(pasajes, RemainderPolicy.ROLL_OVER, comida, pen(1_500)))

        assertEquals(3_500L, dao.funds.getValue("comida").allocatedMinor)
        assertEquals("comida", dao.funds.getValue("pasajes").rolloverToFundId)
    }

    @Test
    fun eventsOfAnUnknownFundAreIgnored() = runTest {
        apply(FundToppedUp(pasajes, pen(100), null))
        apply(UsageDayMarked(pasajes, oct1))
        apply(FundCounted(pasajes, pen(0), null))
        apply(FundClosed(pasajes, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null))
        apply(FundEdited(pasajes, "x", ConsumptionMode.NONE, null, oct1, null))

        assertTrue(dao.funds.isEmpty() && dao.counts.isEmpty() && dao.topUps.isEmpty())
    }

    @Test
    fun resetClearsEverythingTheProjectorDerives() = runTest {
        apply(create())
        apply(FundToppedUp(pasajes, pen(100), null))
        apply(UsageDayMarked(pasajes, oct1))
        apply(FundCounted(pasajes, pen(50), null))

        projector.reset()

        assertTrue(dao.funds.isEmpty() && dao.usageDays.isEmpty() && dao.counts.isEmpty() && dao.topUps.isEmpty())
    }

    @Test
    fun aClosedFundIgnoresTopUpsAndEditsSoTheReplayMatchesTheLiveState() = runTest {
        apply(create())
        apply(FundToppedUp(pasajes, pen(5_000), null))
        apply(FundClosed(pasajes, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null))

        apply(FundToppedUp(pasajes, pen(1_000), null))
        apply(FundEdited(pasajes, "Otro nombre", ConsumptionMode.NONE, null, oct1, null))

        val fund = dao.funds.getValue("pasajes")
        assertEquals(5_000L, fund.allocatedMinor)
        assertEquals("pasajes", fund.name)
        assertEquals(1, dao.topUps.size)
    }

    @Test
    fun rollingOverIntoAClosedFundMovesNothingAndClosingTwiceChangesNothing() = runTest {
        apply(create())
        apply(create(comida, ConsumptionMode.DETAILED, null))
        apply(FundToppedUp(pasajes, pen(5_000), null))
        apply(FundClosed(comida, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null))

        apply(FundClosed(pasajes, RemainderPolicy.ROLL_OVER, comida, pen(5_000)), LocalDate.of(2026, 10, 20))
        assertEquals(0L, dao.funds.getValue("comida").allocatedMinor)
        assertEquals("CLOSED", dao.funds.getValue("pasajes").status.name)

        apply(FundClosed(pasajes, RemainderPolicy.RELEASE_TO_AVAILABLE, null, null), LocalDate.of(2026, 10, 25))
        assertEquals("2026-10-20", dao.funds.getValue("pasajes").closedOn)
    }
}
