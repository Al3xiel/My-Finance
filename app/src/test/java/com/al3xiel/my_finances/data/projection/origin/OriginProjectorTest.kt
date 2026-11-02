package com.al3xiel.my_finances.data.projection.origin

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSourceIds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class OriginProjectorTest {
    private val movementDao = FakeMovementDao()
    private val ledgerDao = FakeLedgerDao()
    private val dao = FakeOriginDao(movementDao, ledgerDao)
    private val movement = MovementProjector(movementDao, ledgerDao)
    private val origin = OriginProjector(dao)
    private var seq = 0L

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private val day = LocalDate.of(2026, 10, 1)

    private suspend fun emit(event: DomainEvent) {
        seq++
        val stored = StoredEvent(seq, "e$seq", 1, "c", EventCodec.encode(event), day, Instant.ofEpochSecond(seq), "t", "p", "h", event)
        movement.apply(stored)
        origin.apply(stored)
    }

    private fun income(id: String, account: String, amount: Long, source: String) =
        IncomeRegistered(MovementId(id), AccountId(account), pen(amount), IncomeSourceId(source), null, null)

    private fun expense(id: String, account: String, vararg parts: Pair<String, Long>) = ExpenseRegistered(
        MovementId(id), AccountId(account), pen(parts.sumOf { it.second }),
        parts.map { (p, a) -> ExpensePartData(PartId(p), pen(a), CategoryId("cat-$p"), emptyList(), null, null) }, null, null,
    )

    private fun created(account: String, opening: Long) =
        AccountCreated(AccountId(account), account, AccountType.BANK, CurrencyCode.PEN, null, pen(opening))

    private fun mix() = dao.balanceRows.values.associate { (it.accountId to it.incomeSourceId) to it.amountMinor }

    @Test fun `opening and income build the mix and an expense takes it in proportion`() = runTest {
        emit(created("A", 0))
        emit(income("m1", "A", 700, "s1"))
        emit(income("m2", "A", 300, "s2"))
        emit(expense("m3", "A", "p1" to 100))

        assertEquals(mapOf(("A" to "s1") to 630L, ("A" to "s2") to 270L), mix())
        assertEquals(
            listOf(70L, 30L),
            dao.attributionRows.values.filter { it.partId == "p1" }.sortedBy { it.incomeSourceId }.map { it.amountMinor },
        )
    }

    @Test fun `the opening balance has its own source`() = runTest {
        emit(created("A", 500))
        assertEquals(mapOf(("A" to OriginSourceIds.OPENING_BALANCE) to 500L), mix())
    }

    @Test fun `a new entry in order uses the fast path without reading the whole ledger`() = runTest {
        emit(created("A", 0))
        emit(income("m1", "A", 100, "s1"))
        val before = dao.fullReads
        emit(income("m2", "A", 50, "s1"))
        emit(expense("m3", "A", "p" to 20))
        assertEquals(before, dao.fullReads)
        assertEquals(mapOf(("A" to "s1") to 130L), mix())
    }

    @Test fun `voiding an old movement recomputes everything`() = runTest {
        emit(created("A", 0))
        emit(income("m1", "A", 100, "s1"))
        emit(income("m2", "A", 100, "s2"))
        emit(expense("m3", "A", "p" to 100))
        emit(MovementVoided(MovementId("m1"), "x", null))

        // sin m1: queda s2 = 100 y el gasto de 100 sale todo de s2
        assertEquals(emptyMap<Pair<String, String>, Long>(), mix())
        assertEquals(listOf("s2" to 100L), dao.attributionRows.values.map { it.incomeSourceId to it.amountMinor })
    }

    @Test fun `correcting an old movement keeps its position and recomputes`() = runTest {
        emit(created("A", 0))
        emit(income("m1", "A", 100, "s1"))
        emit(income("m2", "A", 100, "s2"))
        emit(expense("m3", "A", "p" to 100))
        emit(MovementCorrected(MovementId("m1"), MovementSnapshot.Income(AccountId("A"), pen(300), IncomeSourceId("s1"), null), "x", null))

        // m1 sigue antes que m2: s1 = 300, s2 = 100, el gasto de 100 sale 75/25
        assertEquals(mapOf(("A" to "s1") to 225L, ("A" to "s2") to 75L), mix())
    }

    @Test fun `an expense beyond the balance leaves the unknown source negative`() = runTest {
        emit(created("A", 0))
        emit(income("m1", "A", 100, "s1"))
        emit(expense("m2", "A", "p" to 130))
        assertEquals(mapOf(("A" to OriginSourceIds.UNKNOWN_ADJUSTMENT) to -30L), mix())
    }

    @Test fun `a transfer with a fee moves the mix and the fee is an expense of the origin account`() = runTest {
        emit(created("A", 0))
        emit(created("B", 0))
        emit(income("m1", "A", 1_000, "s1"))
        emit(TransferRegistered(MovementId("t1"), AccountId("A"), AccountId("B"), pen(400), null, null, null))
        assertEquals(mapOf(("A" to "s1") to 600L, ("B" to "s1") to 400L), mix())
        assertEquals(emptyList<OriginAttributionEntity>(), dao.attributionRows.values.toList())
    }

    @Test fun `resetting clears the three tables`() = runTest {
        emit(created("A", 10))
        origin.reset()
        assertEquals(true, dao.balanceRows.isEmpty() && dao.attributionRows.isEmpty() && dao.cursorRow == null)
    }

    @Test fun `while a replay runs apply does nothing and finishing recomputes once`() = runTest {
        emit(created("A", 0))
        emit(income("m1", "A", 100, "s1"))
        origin.reset()
        origin.beginReplay()
        val log = listOf(seq - 1, seq)
        val ev = StoredEvent(seq, "e", 1, "c", EventCodec.encode(income("m1", "A", 100, "s1")), day, Instant.EPOCH, "t", "p", "h", income("m1", "A", 100, "s1"))
        origin.apply(ev)
        assertEquals(emptyMap<Pair<String, String>, Long>(), mix())
        origin.finishReplay()
        assertEquals(mapOf(("A" to "s1") to 100L), mix())
        assertEquals(2, log.size)
    }
}
