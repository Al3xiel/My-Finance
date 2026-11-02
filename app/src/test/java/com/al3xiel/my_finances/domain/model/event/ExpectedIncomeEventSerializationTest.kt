package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

/** Una muestra por evento de ingresos esperados; la suite de serialización general las suma. */
val expectedIncomeSamples: List<DomainEvent> = listOf(
    ExpectedIncomeScheduled(ExpectedIncomeId("e1"), IncomeSourceId("s1"), AccountId("a1"), pen(200_000), LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 5), true),
    ExpectedIncomeEdited(ExpectedIncomeId("e1"), null, AccountId("a2"), pen(210_000), null, LocalDate.of(2026, 11, 6), null),
    ExpectedIncomeConfirmed(ExpectedIncomeId("e1"), MovementId("m1"), ExpectedIncomeId("e2")),
    ExpectedIncomeConfirmed(ExpectedIncomeId("e3"), MovementId("m2"), null),
    ExpectedIncomeCancelled(ExpectedIncomeId("e2"), "se anuló el ingreso"),
    ExpectedIncomeCancelled(ExpectedIncomeId("e4"), null),
)

class ExpectedIncomeEventSerializationTest {
    @Test
    fun everyEventRoundTrips() {
        expectedIncomeSamples.forEach { assertEquals(it, EventCodec.decode(it.type, it.schemaVersion, EventCodec.encode(it))) }
    }

    @Test
    fun everyTypeIsRegisteredAndEncodingIsDeterministic() {
        assertTrue(EventCodec.types.containsAll(expectedIncomeSamples.map { it.type }))
        expectedIncomeSamples.forEach { assertEquals(EventCodec.encode(it), EventCodec.encode(it)) }
    }

    @Test
    fun datesTravelAsIsoText() {
        val text = EventCodec.encode(expectedIncomeSamples.first())

        assertTrue(text, "\"expectedFrom\":\"2026-10-28\"" in text)
        assertTrue(text, "\"expectedTo\":\"2026-11-05\"" in text)
    }

    @Test
    fun theConfirmationCarriesTheNextExpectedIncome() {
        val confirmed = expectedIncomeSamples.filterIsInstance<ExpectedIncomeConfirmed>().first { it.nextExpectedIncomeId != null }

        assertTrue("\"nextExpectedIncomeId\":\"e2\"" in EventCodec.encode(confirmed))
    }

    @Test
    fun theyBelongToTheExpectedIncomeAggregate() {
        expectedIncomeSamples.forEach {
            assertEquals(AggregateType.EXPECTED_INCOME, it.aggregateType)
            assertEquals(1, it.schemaVersion)
        }
        assertFalse(expectedIncomeSamples.isEmpty())
    }
}
