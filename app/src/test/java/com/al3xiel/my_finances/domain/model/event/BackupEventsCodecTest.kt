package com.al3xiel.my_finances.domain.model.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupEventsCodecTest {
    private val samples: List<DomainEvent> = listOf(
        BackupCreated("b1", 120, "a".repeat(64)),
        BackupRestored("b1", 120),
    )

    @Test
    fun everyEventRoundTrips() {
        samples.forEach { assertEquals(it, EventCodec.decode(it.type, it.schemaVersion, EventCodec.encode(it))) }
    }

    @Test
    fun typesAreRegisteredAndEncodingIsDeterministic() {
        assertTrue(EventCodec.types.containsAll(samples.map { it.type }))
        samples.forEach { assertEquals(EventCodec.encode(it), EventCodec.encode(it)) }
    }

    @Test
    fun theBackupIsItsOwnAggregate() {
        samples.forEach {
            assertEquals(AggregateType.BACKUP, it.aggregateType)
            assertEquals("b1", it.aggregateId)
            assertEquals(DomainEvent.CURRENT_SCHEMA_VERSION, it.schemaVersion)
        }
    }

    @Test
    fun thePayloadCarriesNoNamesOrAmounts() {
        assertEquals(
            """{"backupId":"b1","eventCount":120}""",
            EventCodec.encode(BackupRestored("b1", 120)),
        )
    }
}
