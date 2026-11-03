package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerPosterTest {
    private val movements = FakeMovementDao()
    private val ledger = FakeLedgerDao()
    private val poster = LedgerPoster(movements, ledger)

    private fun movement(id: String, sequence: Long) = MovementEntity(
        id, MovementType.REIMBURSEMENT, "bbva", null, 100, CurrencyCode.PEN, null, "mom", null, "2026-10-26",
        MovementStatus.ACTIVE, 1, sequence,
    )

    private fun entry(sequence: Long, delta: Long) =
        LedgerEntryEntity("$sequence-0", sequence, "m$sequence", "bbva", delta, LedgerEntryKind.REIMBURSEMENT_IN, "2026-10-26", false)

    @Test
    fun theBalanceRecordsTheLatestSequenceThatTouchedTheAccount() = runTest {
        poster.post(movement("m3", 3), entry(3, 100))
        poster.post(movement("m7", 7), entry(7, 50))

        assertEquals(150L, ledger.balances.getValue("bbva").balanceMinor)
        assertEquals(7L, ledger.balances.getValue("bbva").lastSequence)
    }

    @Test
    fun aProjectorReplayedAfterAnotherOneNeverMovesTheSequenceBackwards() = runTest {
        // En el replay cada proyector recorre todo el registro por separado: este llega con eventos anteriores
        // a los que otro proyector ya anotó, y el resultado tiene que ser el mismo que en vivo.
        poster.post(movement("m9", 9), entry(9, 100))
        poster.post(movement("m4", 4), entry(4, 50))

        assertEquals(150L, ledger.balances.getValue("bbva").balanceMinor)
        assertEquals(9L, ledger.balances.getValue("bbva").lastSequence)
    }

    @Test
    fun voidingKeepsTheSameRule() = runTest {
        poster.post(movement("m3", 3), entry(3, 100))
        poster.post(movement("m9", 9), entry(9, 50))

        poster.void("m3", sequence = 5)

        assertEquals(50L, ledger.balances.getValue("bbva").balanceMinor)
        assertEquals(9L, ledger.balances.getValue("bbva").lastSequence)
    }
}
