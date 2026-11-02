package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

/** Una muestra por evento de fondos; la suite de serialización general las suma. */
val fundSamples: List<DomainEvent> = listOf(
    FundCreated(FundId("f1"), "Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)),
    FundEdited(FundId("f1"), "Pasajes oct", ConsumptionMode.DAILY_ESTIMATE, pen(600), LocalDate.of(2026, 10, 1), null),
    FundToppedUp(FundId("f1"), pen(5_000), MovementId("m7")),
    FundToppedUp(FundId("f1"), pen(1_000), null),
    UsageDayMarked(FundId("f1"), LocalDate.of(2026, 10, 5)),
    UsageDayUnmarked(FundId("f1"), LocalDate.of(2026, 10, 5)),
    FundCounted(FundId("f1"), pen(4_000), UntrackedSpendData(MovementId("m9"), AccountId("a1"), CategoryId("c1"), pen(500))),
    FundCounted(FundId("f1"), pen(4_500), null),
    FundClosed(FundId("f1"), RemainderPolicy.ROLL_OVER, FundId("f2"), pen(4_500)),
    FundClosed(FundId("f1"), RemainderPolicy.RELEASE_TO_AVAILABLE, null, null),
)

class FundEventSerializationTest {
    @Test
    fun everyEventRoundTrips() {
        fundSamples.forEach { assertEquals(it, EventCodec.decode(it.type, it.schemaVersion, EventCodec.encode(it))) }
    }

    @Test
    fun everyTypeIsRegisteredAndEncodingIsDeterministic() {
        assertTrue(EventCodec.types.containsAll(fundSamples.map { it.type }))
        fundSamples.forEach { assertEquals(EventCodec.encode(it), EventCodec.encode(it)) }
    }

    @Test
    fun datesTravelAsIsoText() {
        val text = EventCodec.encode(fundSamples.first())

        assertTrue(text, "\"startsOn\":\"2026-10-01\"" in text)
        assertTrue(text, "\"endsOn\":\"2026-10-31\"" in text)
    }

    @Test
    fun theUntrackedSpendCarriesTheIdOfItsMovement() {
        val counted = fundSamples.filterIsInstance<FundCounted>().first { it.untrackedSpend != null }

        assertTrue("\"movementId\":\"m9\"" in EventCodec.encode(counted))
        assertEquals(MovementId("m9"), counted.untrackedSpend!!.movementId)
    }

    @Test
    fun theClosureStoresWhatWasRolledOver() {
        val closed = fundSamples.filterIsInstance<FundClosed>().first { it.remainderPolicy == RemainderPolicy.ROLL_OVER }

        assertEquals(pen(4_500), closed.rolledOverAmount)
        assertEquals(FundId("f2"), closed.rollOverToFundId)
    }

    @Test
    fun everyFundEventBelongsToItsFund() {
        fundSamples.forEach {
            assertEquals(AggregateType.FUND, it.aggregateType)
            assertEquals("f1", it.aggregateId)
            assertEquals(1, it.schemaVersion)
        }
        assertFalse(fundSamples.isEmpty())
    }
}
