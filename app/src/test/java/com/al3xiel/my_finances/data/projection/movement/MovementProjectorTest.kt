package com.al3xiel.my_finances.data.projection.movement

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MovementProjectorTest {
    private val movements = FakeMovementDao()
    private val ledger = FakeLedgerDao()
    private val projector = MovementProjector(movements, ledger)

    private val bbva = AccountId("bbva")
    private val cash = AccountId("cash")
    private val day = LocalDate.of(2026, 10, 25)
    private var sequence = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private suspend fun apply(event: DomainEvent, on: LocalDate = day, target: MovementProjector = projector) {
        sequence++
        target.apply(
            StoredEvent(
                sequence = sequence, eventId = "e$sequence", schemaVersion = 1, correlationId = "c",
                payload = EventCodec.encode(event), occurredOn = on, recordedAt = Instant.ofEpochMilli(sequence),
                appVersion = "test", previousHash = "p", hash = "h", event = event,
            ),
        )
    }

    private fun open(id: AccountId, minor: Long) =
        AccountCreated(id, id.value, AccountType.BANK, CurrencyCode.PEN, null, pen(minor))

    private fun part(id: String, minor: Long, category: String = "food", vararg tags: String) =
        ExpensePartData(PartId(id), pen(minor), CategoryId(category), tags.map(::TagId), null, null)

    private fun expense(id: String, account: AccountId, vararg parts: ExpensePartData) =
        ExpenseRegistered(MovementId(id), account, pen(parts.sumOf { it.amount.amountMinor }), parts.toList(), null, "nota")

    private fun balance(account: AccountId) = ledger.balances.getValue(account.value).balanceMinor

    @Test
    fun openingBalanceIsAMovementlessLedgerEntryEvenWhenZero() = runTest {
        apply(open(bbva, 100_000))
        apply(open(cash, 0))

        val entries = ledger.entries.values.toList()
        assertEquals(listOf(LedgerEntryKind.OPENING, LedgerEntryKind.OPENING), entries.map { it.entryKind })
        assertTrue(entries.all { it.movementId == null })
        assertEquals(100_000, balance(bbva))
        assertEquals(0, balance(cash))
        assertTrue(movements.movements.isEmpty())
    }

    @Test
    fun incomeAddsToTheAccount() = runTest {
        apply(open(bbva, 0))
        apply(IncomeRegistered(MovementId("m1"), bbva, pen(5_000), IncomeSourceId("s1"), null, "sueldo"))

        val m = movements.movements.getValue("m1")
        assertEquals(MovementType.INCOME, m.type)
        assertEquals(5_000, m.amountMinor)
        assertEquals("s1", m.incomeSourceId)
        assertEquals(MovementStatus.ACTIVE, m.status)
        assertEquals(1, m.revision)
        assertEquals(5_000, balance(bbva))
    }

    @Test
    fun expenseWithPartsAndTagsSubtractsTheTotalAndMayGoNegative() = runTest {
        apply(open(bbva, 1_000))
        apply(expense("m1", bbva, part("p1", 1_500, "food", "t1", "t2"), part("p2", 500, "home")))

        assertEquals(MovementType.EXPENSE, movements.movements.getValue("m1").type)
        assertEquals(2_000, movements.movements.getValue("m1").amountMinor)
        assertEquals(listOf(1_500L, 500L), movements.parts.values.map { it.amountMinor })
        assertTrue(movements.parts.values.all { it.kind == PartKind.NORMAL })
        assertEquals(setOf("t1", "t2"), movements.partTags.map { it.tagId }.toSet())
        assertEquals(-1_000, balance(bbva))
    }

    @Test
    fun transferMovesMoneyAndFeeBecomesASyntheticPart() = runTest {
        apply(open(bbva, 10_000))
        apply(open(cash, 0))
        apply(TransferRegistered(MovementId("m1"), bbva, cash, pen(2_000), FeeData(pen(50), CategoryId("fees")), null, null))

        assertEquals(7_950, balance(bbva))
        assertEquals(2_000, balance(cash))
        assertEquals(
            setOf(LedgerEntryKind.TRANSFER_OUT, LedgerEntryKind.TRANSFER_IN, LedgerEntryKind.FEE),
            ledger.entries.values.filter { it.movementId == "m1" }.map { it.entryKind }.toSet(),
        )
        val m = movements.movements.getValue("m1")
        assertEquals(MovementType.TRANSFER, m.type)
        assertEquals("cash", m.counterAccountId)
        val fee = movements.parts.values.single()
        assertEquals(PartKind.FEE, fee.kind)
        assertEquals(50, fee.amountMinor)
        assertEquals("fees", fee.categoryId)
    }

    @Test
    fun transferWithoutFeeHasNoParts() = runTest {
        apply(open(bbva, 1_000))
        apply(open(cash, 0))
        apply(TransferRegistered(MovementId("m1"), bbva, cash, pen(300), null, null, null))
        assertTrue(movements.parts.isEmpty())
        assertEquals(700, balance(bbva))
    }

    @Test
    fun voidMarksTheMovementAndItsEntriesAndRestoresTheBalance() = runTest {
        apply(open(bbva, 1_000))
        apply(expense("m1", bbva, part("p1", 400)))
        apply(MovementVoided(MovementId("m1"), "duplicado", null))

        assertEquals(MovementStatus.VOIDED, movements.movements.getValue("m1").status)
        assertTrue(ledger.entries.values.filter { it.movementId == "m1" }.all { it.isVoided })
        assertEquals(1_000, balance(bbva))
        assertEquals(1, movements.parts.size)
    }

    @Test
    fun correctionReplacesPartsAndEntriesAndBumpsTheRevision() = runTest {
        apply(open(bbva, 1_000))
        apply(expense("m1", bbva, part("p1", 400, "food", "t1"), part("p2", 100, "home")))
        apply(
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Expense(bbva, pen(300), listOf(part("p1", 300, "food", "t2")), "corregido"),
                "monto mal", null,
            ),
            on = LocalDate.of(2026, 10, 24),
        )

        val m = movements.movements.getValue("m1")
        assertEquals(2, m.revision)
        assertEquals(300, m.amountMinor)
        assertEquals("corregido", m.note)
        assertEquals("2026-10-24", m.occurredOn)
        assertEquals(listOf("p1"), movements.parts.values.map { it.id })
        assertEquals(setOf("t2"), movements.partTags.map { it.tagId }.toSet())
        assertEquals(700, balance(bbva))
        val own = ledger.entries.values.filter { it.movementId == "m1" }
        assertEquals(listOf(true, false), own.map { it.isVoided })
    }

    @Test
    fun correctionCanMoveAnIncomeToAnotherAccount() = runTest {
        apply(open(bbva, 0))
        apply(open(cash, 0))
        apply(IncomeRegistered(MovementId("m1"), bbva, pen(500), IncomeSourceId("s1"), null, null))
        apply(
            MovementCorrected(
                MovementId("m1"),
                MovementSnapshot.Income(cash, pen(500), IncomeSourceId("s1"), null),
                "cuenta equivocada", null,
            ),
        )
        assertEquals(0, balance(bbva))
        assertEquals(500, balance(cash))
    }

    @Test
    fun transfersCannotBeVoidedOrCorrected() = runTest {
        apply(open(bbva, 1_000))
        apply(open(cash, 0))
        apply(TransferRegistered(MovementId("m1"), bbva, cash, pen(300), null, null, null))
        apply(MovementVoided(MovementId("m1"), "x", null))
        apply(MovementCorrected(MovementId("m1"), MovementSnapshot.Income(bbva, pen(1), IncomeSourceId("s"), null), "x", null))

        assertEquals(MovementStatus.ACTIVE, movements.movements.getValue("m1").status)
        assertEquals(700, balance(bbva))
        assertEquals(300, balance(cash))
    }

    @Test
    fun voidOrCorrectionOfUnknownOrVoidedMovementIsIgnored() = runTest {
        apply(open(bbva, 1_000))
        apply(MovementVoided(MovementId("ghost"), "x", null))
        apply(expense("m1", bbva, part("p1", 400)))
        apply(MovementVoided(MovementId("m1"), "x", null))
        apply(MovementCorrected(MovementId("m1"), MovementSnapshot.Expense(bbva, pen(1), listOf(part("p1", 1)), null), "x", null))

        assertNull(movements.movements["ghost"])
        assertEquals(MovementStatus.VOIDED, movements.movements.getValue("m1").status)
        assertEquals(1_000, balance(bbva))
    }

    @Test
    fun replayProducesTheSameProjection() = runTest {
        suspend fun script(target: MovementProjector) {
            sequence = 0
            apply(open(bbva, 10_000), target = target)
            apply(open(cash, 0), target = target)
            apply(expense("m1", bbva, part("p1", 400, "food", "t1")), target = target)
            apply(TransferRegistered(MovementId("m2"), bbva, cash, pen(1_000), FeeData(pen(20), CategoryId("fees")), null, null), target = target)
            apply(MovementVoided(MovementId("m1"), "x", null), target = target)
            apply(IncomeRegistered(MovementId("m3"), cash, pen(700), IncomeSourceId("s1"), null, "sueldo"), target = target)
            apply(
                MovementCorrected(MovementId("m3"), MovementSnapshot.Income(bbva, pen(900), IncomeSourceId("s1"), null), "cuenta equivocada", null),
                target = target,
            )
            apply(expense("m4", cash, part("p9", 200, "food", "t1")), target = target)
            apply(
                MovementCorrected(MovementId("m4"), MovementSnapshot.Expense(bbva, pen(250), listOf(part("p9", 250, "home", "t2")), "x"), "cambio", null),
                target = target,
            )
        }
        script(projector)
        val first = listOf(movements.movements.toMap(), movements.parts.toMap(), movements.partTags.toSet(), ledger.entries.toMap(), ledger.balances.toMap())

        projector.reset()
        assertTrue(movements.movements.isEmpty() && ledger.entries.isEmpty() && ledger.balances.isEmpty())
        script(projector)
        assertEquals(first, listOf(movements.movements.toMap(), movements.parts.toMap(), movements.partTags.toSet(), ledger.entries.toMap(), ledger.balances.toMap()))
    }
}
