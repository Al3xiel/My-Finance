package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

/** Una muestra por evento de conciliación; la suite de serialización general las suma. */
val reconciliationSamples: List<DomainEvent> = listOf(
    BalanceCheckRecorded(ReconciliationId("r1"), AccountId("a1"), pen(81_240), pen(80_000)),
    BalanceCheckRecorded(ReconciliationId("r2"), AccountId("a1"), pen(-1_500), pen(0)),
    MovementLinkedToReconciliation(ReconciliationId("r1"), MovementId("m1")),
    ReconciliationResolved(
        ReconciliationId("r1"),
        ReconciliationCause.UNKNOWN,
        AdjustmentData(MovementId("m9"), AdjustmentDirection.UNIDENTIFIED_INCOME, pen(240)),
        "no encontré el origen",
    ),
    ReconciliationResolved(ReconciliationId("r2"), ReconciliationCause.BALANCED, null, null),
    ReconciliationCancelled(ReconciliationId("r2"), "me equivoqué de cuenta"),
)

class ReconciliationEventSerializationTest {
    @Test
    fun everyEventRoundTrips() {
        reconciliationSamples.forEach { assertEquals(it, EventCodec.decode(it.type, it.schemaVersion, EventCodec.encode(it))) }
    }

    @Test
    fun everyTypeIsRegisteredAndEncodingIsDeterministic() {
        assertTrue(EventCodec.types.containsAll(reconciliationSamples.map { it.type }))
        reconciliationSamples.forEach { assertEquals(EventCodec.encode(it), EventCodec.encode(it)) }
    }

    @Test
    fun aggregateIsTheReconciliation() {
        reconciliationSamples.forEach {
            assertEquals(AggregateType.RECONCILIATION, it.aggregateType)
            assertEquals(DomainEvent.CURRENT_SCHEMA_VERSION, it.schemaVersion)
        }
        assertEquals("r1", reconciliationSamples.first().aggregateId)
    }

    @Test
    fun checkPayloadHasNoTimestampBecauseTheEventRecordsIt() {
        assertFalse("checkedAt" in EventCodec.encode(reconciliationSamples.first()))
    }

    @Test
    fun rebuildReasonsAreFixed() {
        assertEquals(setOf("VERSION_CHANGE", "MANUAL", "RESTORE"), RebuildReason.ALL)
    }
}
