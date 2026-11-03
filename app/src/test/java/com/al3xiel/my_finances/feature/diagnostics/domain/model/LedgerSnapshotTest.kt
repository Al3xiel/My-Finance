package com.al3xiel.my_finances.feature.diagnostics.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun onlyTheEntriesOfTheChosenEventsEnterAndTheBalanceRunsOverThem() {
        val cut = LedgerSnapshot.apply(ledger, SnapshotCut(setOf(1, 3, 6)))
        assertEquals(listOf(1L, 3L, 6L), cut.map { it.sequence })
        assertEquals(listOf(500L, 1_500L, 1_200L), cut.map { it.balanceMinor })
        assertEquals(mapOf("a1" to 1_200L), LedgerSnapshot.balances(cut))
    }

    @Test
    fun anEntryOfALaterEventStaysInWhenThatEventTravels() {
        val cut = LedgerSnapshot.apply(ledger, SnapshotCut(setOf(1, 3, 6, 9)))
        assertEquals(listOf(1L, 3L, 6L, 9L), cut.map { it.sequence })
        assertEquals(1_900L, cut.last().balanceMinor)
    }

    @Test
    fun theAdjustmentOfAResolutionComesInThroughTheSequenceOfItsReconciliationEvent() {
        // El ajuste no tiene eventos de movimiento propios: nace del evento de conciliación (sequence 8), posterior al chequeo.
        val withAdjustment = ledger + line(8, "adjustment-1", 100)
        val travels = LedgerSnapshot.apply(withAdjustment, SnapshotCut(setOf(1, 3, 6, 8)))
        assertEquals(listOf(1L, 3L, 6L, 8L), travels.map { it.sequence })
        assertEquals(1_300L, travels.last().balanceMinor)
    }

    @Test
    fun anEntryVoidedByAnEventThatDoesNotTravelCountsAsNotVoided() {
        val voidedLater = listOf(line(3, "m1", 1_000, voided = true, voidedAt = 8))
        val cut = LedgerSnapshot.apply(voidedLater, SnapshotCut(setOf(3)))
        assertFalse(cut.single().isVoided)
        assertNull(cut.single().voidedSequence)
        assertEquals(1_000L, cut.single().balanceMinor)
    }

    @Test
    fun anEntryVoidedByAnEventThatTravelsStaysVoidedAndAddsNothing() {
        val voidedEarly = listOf(line(3, "m1", 1_000, voided = true, voidedAt = 4), line(5, "m2", 200))
        val cut = LedgerSnapshot.apply(voidedEarly, SnapshotCut(setOf(3, 4, 5)))
        assertTrue(cut.first().isVoided)
        assertEquals(4L, cut.first().voidedSequence)
        assertEquals(listOf(0L, 200L), cut.map { it.balanceMinor })
    }

    @Test
    fun anEntryVoidedBeforeTheColumnExistedStaysVoidedBecauseNobodyKnowsWhoVoidedIt() {
        val migrated = listOf(line(3, "m1", 1_000, voided = true, voidedAt = null), line(5, "m2", 200))
        val cut = LedgerSnapshot.apply(migrated, SnapshotCut(setOf(3, 5)))
        assertTrue(cut.first().isVoided)
        assertEquals(listOf(0L, 200L), cut.map { it.balanceMinor })
        // Sin corte (el lado del espacio) se comporta igual: la comparación no inventa una divergencia.
        assertEquals(cut.map { it.balanceMinor }, LedgerSnapshot.apply(migrated, null).map { it.balanceMinor })
    }

    @Test
    fun withoutACutEveryEntryEntersAndTheBalanceIsRecomputed() {
        val all = LedgerSnapshot.apply(ledger, null)
        assertEquals(ledger.map { it.sequence }, all.map { it.sequence })
        assertEquals(1_900L, all.last().balanceMinor)
    }

    @Test
    fun balancesAreKeptPerAccount() {
        val lines = listOf(line(1, "m1", 100, "a1"), line(2, "m2", 50, "a2"), line(3, "m3", -20, "a1"))
        assertEquals(mapOf("a1" to 80L, "a2" to 50L), LedgerSnapshot.balances(LedgerSnapshot.apply(lines, SnapshotCut(setOf(1, 2, 3)))))
    }
}
