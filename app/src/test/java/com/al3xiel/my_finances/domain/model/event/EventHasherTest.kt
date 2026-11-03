package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.domain.model.AccountId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EventHasherTest {
    private val base = HashedFields(
        previousHash = EventHasher.GENESIS,
        eventId = "e1",
        type = "AccountRenamed",
        schemaVersion = 1,
        aggregateType = "ACCOUNT",
        aggregateId = "a1",
        correlationId = "c1",
        payload = """{"x":1}""",
        occurredOn = "2026-10-25",
        recordedAt = 1_000,
        appVersion = "1.0",
    )

    @Test
    fun matchesKnownVector() {
        assertEquals("257aea0748e4ea2b32d4f3a2db5a588c7b90b83bb51d3056825da38913ca4ebc", EventHasher.hash(base))
    }

    @Test
    fun changingAnyColumnChangesTheHash() {
        val original = EventHasher.hash(base)
        val tampered = listOf(
            base.copy(previousHash = "other"),
            base.copy(eventId = "e2"),
            base.copy(type = "AccountArchived"),
            base.copy(schemaVersion = 2),
            base.copy(aggregateType = "CATEGORY"),
            base.copy(aggregateId = "a2"),
            base.copy(correlationId = "c2"),
            base.copy(payload = """{"x":2}"""),
            base.copy(occurredOn = "2026-10-26"),
            base.copy(recordedAt = 1_001),
            base.copy(appVersion = "1.1"),
        )
        tampered.forEach { assertNotEquals(original, EventHasher.hash(it)) }
        assertEquals(tampered.size, tampered.map(EventHasher::hash).toSet().size)
    }

    @Test
    fun separatorKeepsFieldBoundaries() {
        val a = EventHasher.hash(base.copy(type = "ab", aggregateId = "c"))
        val b = EventHasher.hash(base.copy(type = "a", aggregateId = "bc"))
        assertNotEquals(a, b)
    }

    @Test
    fun storedEventOverloadUsesTheStoredPayloadTextAndEveryColumn() {
        val stored = StoredEvent(
            sequence = 1, eventId = "e1", schemaVersion = 1, correlationId = "c1", payload = """{"x":1}""",
            occurredOn = LocalDate.of(2026, 10, 25), recordedAt = Instant.ofEpochMilli(1_000), appVersion = "1.0",
            previousHash = EventHasher.GENESIS, hash = "", event = AccountRenamed(AccountId("a1"), "x"),
        )
        assertEquals(EventHasher.hash(base), EventHasher.hash(stored))
    }
}
