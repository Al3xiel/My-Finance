package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** `voided_sequence` deja saber desde qué evento un asiento dejó de contar (D30). */
class MovementProjectorVoidedSequenceTest {
    private val movements = FakeMovementDao()
    private val ledger = FakeLedgerDao()
    private val projector = MovementProjector(movements, ledger)
    private val bbva = AccountId("bbva")
    private var sequence = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun apply(event: DomainEvent) {
        sequence++
        projector.apply(
            StoredEvent(
                sequence, "e$sequence", 1, "c", EventCodec.encode(event), LocalDate.of(2026, 10, 25),
                Instant.ofEpochMilli(sequence), "test", "p", "h", event,
            ),
        )
    }

    private fun income(minor: Long) = IncomeRegistered(MovementId("m1"), bbva, pen(minor), IncomeSourceId("s"), null, null)

    @Test
    fun voidingStampsTheSequenceOfTheVoidingEvent() = runTest {
        apply(AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(0)))
        apply(income(500))
        apply(MovementVoided(MovementId("m1"), "duplicado", null))

        val entry = ledger.entries.values.single { it.movementId == "m1" }
        assertEquals(3L, entry.voidedSequence)
        assertEquals(true, entry.isVoided)
    }

    @Test
    fun correctingStampsTheOldEntryAndLeavesTheNewOneOpen() = runTest {
        apply(AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(0)))
        apply(income(500))
        apply(MovementCorrected(MovementId("m1"), MovementSnapshot.Income(bbva, pen(700), IncomeSourceId("s"), null), "monto", null))

        val (old, current) = ledger.entries.values.filter { it.movementId == "m1" }
        assertEquals(3L, old.voidedSequence)
        assertNull(current.voidedSequence)
        assertEquals(false, current.isVoided)
    }

    @Test
    fun openEntriesNeverHaveAVoidedSequence() = runTest {
        apply(AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(100)))
        apply(income(500))

        assertEquals(listOf<Long?>(null, null), ledger.entries.values.map { it.voidedSequence })
    }

    @Test
    fun theProjectorVersionIsRaised() {
        assertEquals(4, projector.version)
    }
}
