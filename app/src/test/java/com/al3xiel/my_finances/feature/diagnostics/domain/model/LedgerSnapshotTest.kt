package com.al3xiel.my_finances.feature.diagnostics.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerSnapshotTest {
    private fun line(sequence: Long, movement: String?, delta: Long, account: String = "a1", voided: Boolean = false, voidedAt: Long? = null) =
        ExportedLedgerLine(sequence, movement, account, delta, "INCOME", "2026-10-25", voided, voidedAt, 0)

    private val ledger = listOf(
        line(1, null, 500),
        line(3, "m1", 1_000),
        line(6, "m2", -300),
        line(9, "m3", 700),
    )

    @Test
    fun entriesAfterTheCutAreLeftOutAndTheBalanceRunsOverTheRest() {
        val cut = LedgerSnapshot.apply(ledger, SnapshotCut(upToSequence = 6))
        assertEquals(listOf(1L, 3L, 6L), cut.map { it.sequence })
        assertEquals(listOf(500L, 1_500L, 1_200L), cut.map { it.balanceMinor })
        assertEquals(mapOf("a1" to 1_200L), LedgerSnapshot.balances(cut))
    }

    @Test
    fun aLaterMovementThatWasLinkedStaysIn() {
        val cut = LedgerSnapshot.apply(ledger, SnapshotCut(6, setOf("m3")))
        assertEquals(listOf(1L, 3L, 6L, 9L), cut.map { it.sequence })
        assertEquals(1_900L, cut.last().balanceMinor)
    }

    @Test
    fun anEntryVoidedAfterTheCutCountsAsNotVoidedBecauseTheVoidingEventDoesNotTravel() {
        val voidedLater = listOf(line(3, "m1", 1_000, voided = true, voidedAt = 8))
        val cut = LedgerSnapshot.apply(voidedLater, SnapshotCut(6))
        assertFalse(cut.single().isVoided)
        assertEquals(null, cut.single().voidedSequence)
        assertEquals(1_000L, cut.single().balanceMinor)
    }

    @Test
    fun anEntryVoidedBeforeTheCutStaysVoidedAndAddsNothing() {
        val voidedEarly = listOf(line(3, "m1", 1_000, voided = true, voidedAt = 4), line(5, "m2", 200))
        val cut = LedgerSnapshot.apply(voidedEarly, SnapshotCut(6))
        assertTrue(cut.first().isVoided)
        assertEquals(listOf(0L, 200L), cut.map { it.balanceMinor })
    }

    @Test
    fun aLinkedMovementKeepsItsFinalVoidedState() {
        val voidedLater = listOf(line(3, "m1", 1_000, voided = true, voidedAt = 8))
        val cut = LedgerSnapshot.apply(voidedLater, SnapshotCut(6, setOf("m1")))
        assertTrue(cut.single().isVoided)
        assertEquals(8L, cut.single().voidedSequence)
    }

    @Test
    fun balancesAreKeptPerAccount() {
        val lines = listOf(line(1, "m1", 100, "a1"), line(2, "m2", 50, "a2"), line(3, "m3", -20, "a1"))
        assertEquals(mapOf("a1" to 80L, "a2" to 50L), LedgerSnapshot.balances(LedgerSnapshot.apply(lines, SnapshotCut(10))))
    }
}
