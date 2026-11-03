package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.domain.model.ReconciliationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventsCodecTest {
    private val map = linkedMapOf(
        "Account-A" to EntityRef(EntityType.ACCOUNT, "acc-1"),
        "Person-1" to EntityRef(EntityType.PERSON, "per-1"),
    )
    private val fromReconciliation = DiagnosticExportCreated(
        exportId = "x1",
        reconciliationId = ReconciliationId("rec-1"),
        scope = DiagnosticScope.RECONCILIATION,
        anonymized = true,
        exportedUpToSequence = 482,
        pseudonymMap = map,
        eventCount = 40,
        packageHash = "a".repeat(64),
    )
    private val fromDevTools = fromReconciliation.copy(reconciliationId = null, scope = DiagnosticScope.FULL)
    private val dictionary = DiagnosticDictionaryExported(
        dictionaryId = "d1",
        exportId = "x1",
        reconciliationId = ReconciliationId("rec-1"),
        scope = DictionaryScope.PARTIAL,
        revealedPseudonyms = listOf("Account-A"),
        revealedNoteEventIds = emptyList(),
    )
    private val samples: List<DomainEvent> = listOf(fromReconciliation, fromDevTools, dictionary)

    @Test
    fun everyEventRoundTrips() {
        samples.forEach { assertEquals(it, EventCodec.decode(it.type, it.schemaVersion, EventCodec.encode(it))) }
    }

    @Test
    fun typesAreRegisteredAndEncodingIsDeterministic() {
        assertTrue(EventCodec.types.containsAll(samples.map { it.type }.toSet()))
        samples.forEach { assertEquals(EventCodec.encode(it), EventCodec.encode(it)) }
        assertTrue(samples.all { it.schemaVersion == 1 })
    }

    @Test
    fun anExportFromAReconciliationBelongsToIt() {
        assertEquals(AggregateType.RECONCILIATION, fromReconciliation.aggregateType)
        assertEquals("rec-1", fromReconciliation.aggregateId)
    }

    @Test
    fun anExportFromDevToolsBelongsToTheSystemAndIsKeyedByItsExportId() {
        assertEquals(AggregateType.SYSTEM, fromDevTools.aggregateType)
        assertEquals("x1", fromDevTools.aggregateId)
    }

    @Test
    fun theDictionaryEventBelongsToTheReconciliationThatMadeThePackage() {
        assertEquals(AggregateType.RECONCILIATION, dictionary.aggregateType)
        assertEquals("rec-1", dictionary.aggregateId)
    }

    @Test
    fun thePayloadKeepsReferencesNeverTheRealAlias() {
        val payload = EventCodec.encode(fromReconciliation)

        assertTrue(payload.contains("\"entityId\":\"acc-1\""))
        assertFalse(payload.contains("alias"))
        assertFalse(payload.contains("note"))
    }

    @Test
    fun theMapKeepsItsPseudonymsInOrder() {
        val decoded = EventCodec.decode(fromReconciliation.type, 1, EventCodec.encode(fromReconciliation)) as DiagnosticExportCreated

        assertEquals(listOf("Account-A", "Person-1"), decoded.pseudonymMap.keys.toList())
    }
}
