package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.FundCounted
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.UntrackedSpendData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MovementProjectorFundTest {
    private val movements = FakeMovementDao()
    private val ledger = FakeLedgerDao()
    private val projector = MovementProjector(movements, ledger)

    private val cash = AccountId("cash")
    private val day = LocalDate.of(2026, 10, 25)
    private var sequence = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun apply(event: DomainEvent, on: LocalDate = day) {
        sequence++
        projector.apply(
            StoredEvent(
                sequence = sequence, eventId = "e$sequence", schemaVersion = 1, correlationId = "c",
                payload = EventCodec.encode(event), occurredOn = on, recordedAt = Instant.ofEpochMilli(sequence),
                appVersion = "test", previousHash = "p", hash = "h", event = event,
            ),
        )
    }

    private fun open(minor: Long) = AccountCreated(cash, "Efectivo", AccountType.CASH, CurrencyCode.PEN, null, pen(minor))

    private fun part(id: String, minor: Long, fund: String?) =
        ExpensePartData(PartId(id), pen(minor), CategoryId("transport"), emptyList(), null, fund?.let(::FundId))

    private fun expense(id: String, vararg parts: ExpensePartData) =
        ExpenseRegistered(MovementId(id), cash, pen(parts.sumOf { it.amount.amountMinor }), parts.toList(), null, null)

    private fun counted(movementId: String, minor: Long = 500) = FundCounted(
        FundId("f1"), pen(4_000), UntrackedSpendData(MovementId(movementId), cash, CategoryId("transport"), pen(minor)),
    )

    private suspend fun balance() = ledger.balanceOf(cash.value)

    @Test
    fun theProjectorVersionIsRaisedSoReplayFillsTheFundColumn() {
        assertEquals(3, projector.version)
    }

    @Test
    fun aPartRemembersItsFund() = runTest {
        apply(open(10_000))
        apply(expense("m1", part("p1", 500, "f1"), part("p2", 100, null)))

        assertEquals("f1", movements.parts.getValue("p1").fundId)
        assertNull(movements.parts.getValue("p2").fundId)
    }

    @Test
    fun aCorrectionUpdatesTheFundOfTheSurvivingPartsAndStoresTheNewOnes() = runTest {
        apply(open(10_000))
        apply(expense("m1", part("p1", 500, "f1")))

        apply(
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(cash, pen(800), listOf(part("p1", 500, "f2"), part("p3", 300, "f1")), null),
                "fondo equivocado", null,
            ),
        )

        assertEquals("f2", movements.parts.getValue("p1").fundId)
        assertEquals("f1", movements.parts.getValue("p3").fundId)
    }

    @Test
    fun aCountWithUntrackedSpendCreatesAnExpenseLikeMovementWithASyntheticPart() = runTest {
        apply(open(10_000))

        apply(counted("m9", minor = 700), LocalDate.of(2026, 10, 26))

        val movement = movements.movements.getValue("m9")
        assertEquals(MovementType.FUND_UNTRACKED_SPEND, movement.type)
        assertEquals(MovementStatus.ACTIVE, movement.status)
        assertEquals(700L, movement.amountMinor)
        assertEquals("2026-10-26", movement.occurredOn)
        assertEquals(cash.value, movement.accountId)
        val entry = ledger.entries.values.last()
        assertEquals(LedgerEntryKind.EXPENSE, entry.entryKind)
        assertEquals(-700L, entry.deltaMinor)
        assertEquals(9_300L, balance())
        val synthetic = movements.parts.getValue("m9-untracked")
        assertEquals(PartKind.UNTRACKED, synthetic.kind)
        assertEquals("transport", synthetic.categoryId)
        assertEquals(700L, synthetic.amountMinor)
        assertNull(synthetic.fundId)
    }

    @Test
    fun aCountWithoutUntrackedSpendCreatesNothing() = runTest {
        apply(open(10_000))

        apply(FundCounted(FundId("f1"), pen(4_000), null))

        assertTrue(movements.movements.isEmpty())
        assertEquals(10_000L, balance())
    }

    @Test
    fun theUntrackedSpendCanBeNeitherVoidedNorCorrected() = runTest {
        apply(open(10_000))
        apply(counted("m9"))

        apply(MovementVoided(MovementId("m9"), "equivocado", null))
        apply(
            MovementCorrected(
                MovementId("m9"),
                MovementSnapshot.Expense(cash, pen(100), listOf(part("m9-untracked", 100, null)), null),
                "monto mal", null,
            ),
        )

        assertEquals(MovementStatus.ACTIVE, movements.movements.getValue("m9").status)
        assertEquals(500L, movements.movements.getValue("m9").amountMinor)
        assertEquals(9_500L, balance())
    }

    @Test
    fun replayProducesTheSameProjection() = runTest {
        suspend fun script() {
            sequence = 0
            apply(open(10_000))
            apply(expense("m1", part("p1", 500, "f1")))
            apply(counted("m9"), LocalDate.of(2026, 10, 26))
        }

        script()
        val first = listOf(movements.movements.toMap(), movements.parts.toMap(), ledger.entries.toMap(), ledger.balances.toMap())

        projector.reset()
        script()

        assertEquals(first, listOf(movements.movements.toMap(), movements.parts.toMap(), ledger.entries.toMap(), ledger.balances.toMap()))
    }
}
