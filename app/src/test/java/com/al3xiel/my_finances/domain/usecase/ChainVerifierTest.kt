package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.HashedFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainVerifierTest {
    private fun chain(size: Int): MutableList<ChainRow> {
        val rows = mutableListOf<ChainRow>()
        var previous = EventHasher.GENESIS
        for (i in 1..size) {
            val fields = HashedFields(
                previousHash = previous, eventId = "e$i", type = "AccountRenamed", schemaVersion = 1,
                aggregateType = "ACCOUNT", aggregateId = "a1", correlationId = "c", payload = "{\"n\":$i}",
                occurredOn = "2026-10-25", recordedAt = 1_000L + i, appVersion = "test",
            )
            val hash = EventHasher.hash(fields)
            rows += ChainRow(i.toLong(), hash, fields)
            previous = hash
        }
        return rows
    }

    private fun ChainRow.withFields(change: (HashedFields) -> HashedFields) = copy(fields = change(fields))

    private fun verify(rows: List<ChainRow>): ChainVerification {
        val verifier = ChainVerifier()
        for (row in rows) verifier.accept(row)?.let { return it }
        return verifier.result()
    }

    @Test
    fun emptyAndIntactChainsAreValid() {
        assertEquals(ChainVerification.Valid(0), verify(emptyList()))
        assertEquals(ChainVerification.Valid(4), verify(chain(4)))
    }

    @Test
    fun lastHashIsTheHashOfTheLastRow() {
        val rows = chain(3)
        val verifier = ChainVerifier()
        assertEquals(EventHasher.GENESIS, verifier.lastHash)
        rows.forEach { assertNull(verifier.accept(it)) }
        assertEquals(rows.last().hash, verifier.lastHash)
    }

    @Test
    fun alteredPayloadBreaksAtThatRow() {
        val rows = chain(3)
        rows[1] = rows[1].withFields { it.copy(payload = "{\"n\":99}") }
        assertEquals(ChainVerification.Broken(2, ChainVerification.Reason.HASH_MISMATCH), verify(rows))
    }

    @Test
    fun deletedRowBreaksTheSequence() {
        val rows = chain(3)
        rows.removeAt(1)
        assertEquals(ChainVerification.Broken(3, ChainVerification.Reason.SEQUENCE_GAP), verify(rows))
    }

    @Test
    fun swappedRowsBreakTheSequence() {
        val rows = chain(3)
        val second = rows[1]
        rows[1] = rows[2]
        rows[2] = second
        assertEquals(ChainVerification.Broken(3, ChainVerification.Reason.SEQUENCE_GAP), verify(rows))
    }

    @Test
    fun brokenPreviousHashBreaksTheLink() {
        val rows = chain(3)
        rows[2] = rows[2].withFields { it.copy(previousHash = "forged") }
        assertEquals(ChainVerification.Broken(3, ChainVerification.Reason.BROKEN_LINK), verify(rows))
    }

    @Test
    fun firstRowMustStartFromGenesis() {
        val rows = chain(2)
        rows[0] = rows[0].withFields { it.copy(previousHash = "forged") }
        assertEquals(ChainVerification.Broken(1, ChainVerification.Reason.BROKEN_LINK), verify(rows))
    }

    @Test
    fun repeatedSequenceBreaksTheSequence() {
        val rows = chain(3)
        rows[2] = rows[2].copy(sequence = 2)
        assertEquals(ChainVerification.Broken(2, ChainVerification.Reason.SEQUENCE_GAP), verify(rows))
    }

    @Test
    fun repeatedEventIdIsRejectedEvenWithAValidHash() {
        val rows = mutableListOf<ChainRow>()
        var previous = EventHasher.GENESIS
        for (i in 1..2) {
            val fields = HashedFields(previous, "same", "AccountRenamed", 1, "ACCOUNT", "a1", "c", "{}", "2026-10-25", 1_000L + i, "test")
            val hash = EventHasher.hash(fields)
            rows += ChainRow(i.toLong(), hash, fields)
            previous = hash
        }
        assertEquals(ChainVerification.Broken(2, ChainVerification.Reason.DUPLICATE_EVENT_ID), verify(rows))
    }

    @Test
    fun anAlteredStoredHashIsDetected() {
        val rows = chain(2)
        rows[0] = rows[0].copy(hash = "0".repeat(64))
        assertEquals(ChainVerification.Broken(1, ChainVerification.Reason.HASH_MISMATCH), verify(rows))
    }
}
