package com.al3xiel.my_finances.data.projection.loan

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.movement.FakeLedgerDao
import com.al3xiel.my_finances.data.projection.movement.FakeMovementDao
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.RepaymentId
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.LoanGranted
import com.al3xiel.my_finances.domain.model.event.LoanRepaymentReceived
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class LoanProjectorTest {
    private val movementDao = FakeMovementDao()
    private val ledgerDao = FakeLedgerDao()
    private val dao = FakeLoanDao(movementDao, ledgerDao)
    private val projector = LoanProjector(dao, LedgerPoster(movementDao, ledgerDao))

    private val bbva = AccountId("bbva")
    private val pepe = PersonId("pepe")
    private val day = LocalDate.of(2026, 10, 20)
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

    private fun grant(minor: Long = 5_000) = LoanGranted(LoanId("l1"), MovementId("ml1"), pepe, bbva, pen(minor), "taxi")

    private fun repay(id: String, minor: Long) =
        LoanRepaymentReceived(RepaymentId("rp$id"), MovementId("mr$id"), LoanId("l1"), bbva, pen(minor))

    private suspend fun balance() = ledgerDao.balanceOf(bbva.value)

    @Test
    fun grantingALoanTakesMoneyOutWithoutBeingAnExpense() = runTest {
        apply(grant())

        val movement = movementDao.movements.getValue("ml1")
        assertEquals(MovementType.LOAN_DISBURSEMENT, movement.type)
        assertEquals("pepe", movement.personId)
        assertEquals("taxi", movement.note)
        val entry = ledgerDao.entries.values.single()
        assertEquals(LedgerEntryKind.LOAN_OUT, entry.entryKind)
        assertEquals(-5_000L, entry.deltaMinor)
        assertEquals(-5_000L, balance())
        assertTrue(movementDao.parts.isEmpty())
        val loan = dao.loans.getValue("l1")
        assertEquals(5_000L, loan.principalMinor)
        assertEquals(0L, loan.repaidMinor)
        assertEquals(LoanStatus.OPEN, loan.status)
        assertEquals("2026-10-20", loan.occurredOn)
    }

    @Test
    fun repaymentsBringMoneyInAndTheLoanIsRepaidWhenTheyCoverThePrincipal() = runTest {
        apply(grant())
        apply(repay("1", 2_000))

        val partial = dao.loans.getValue("l1")
        assertEquals(2_000L, partial.repaidMinor)
        assertEquals(LoanStatus.OPEN, partial.status)
        val movement = movementDao.movements.getValue("mr1")
        assertEquals(MovementType.LOAN_REPAYMENT, movement.type)
        assertEquals("pepe", movement.personId)
        assertEquals(SystemCatalog.incomeSources.single { it.name == "Pago de préstamo" }.id.value, movement.incomeSourceId)
        assertEquals(LedgerEntryKind.LOAN_IN, ledgerDao.entries.values.last().entryKind)
        assertEquals(-3_000L, balance())

        apply(repay("2", 3_000))
        assertEquals(5_000L, dao.loans.getValue("l1").repaidMinor)
        assertEquals(LoanStatus.REPAID, dao.loans.getValue("l1").status)
    }

    @Test
    fun voidingARepaymentRecalculatesTheLoan() = runTest {
        apply(grant())
        apply(repay("1", 5_000))
        assertEquals(LoanStatus.REPAID, dao.loans.getValue("l1").status)

        apply(MovementVoided(MovementId("mr1"), "error", null))

        assertEquals(MovementStatus.VOIDED, movementDao.movements.getValue("mr1").status)
        assertEquals(0L, dao.loans.getValue("l1").repaidMinor)
        assertEquals(LoanStatus.OPEN, dao.loans.getValue("l1").status)
        assertTrue(dao.repayments.isEmpty())
        assertEquals(-5_000L, balance())
    }

    @Test
    fun voidingTheLoanGivesTheMoneyBack() = runTest {
        apply(grant())

        apply(MovementVoided(MovementId("ml1"), "error", null))

        assertEquals(MovementStatus.VOIDED, movementDao.movements.getValue("ml1").status)
        assertEquals(0L, balance())
    }

    @Test
    fun voidingAnotherMovementOrTwiceChangesNothing() = runTest {
        apply(grant())
        apply(MovementVoided(MovementId("someone-else"), "x", null))
        apply(MovementVoided(MovementId("ml1"), "x", null))
        apply(MovementVoided(MovementId("ml1"), "otra vez", null))

        assertEquals(1, ledgerDao.entries.size)
        assertEquals(0L, balance())
    }

    @Test
    fun replayProducesTheSameProjection() = runTest {
        suspend fun script() {
            sequence = 0
            apply(grant())
            apply(repay("1", 2_000), day.plusDays(1))
            apply(repay("2", 3_000), day.plusDays(2))
            apply(MovementVoided(MovementId("mr1"), "error", null), day.plusDays(3))
        }

        script()
        val first = listOf(movementDao.movements.toMap(), ledgerDao.entries.toMap(), ledgerDao.balances.toMap(), dao.loans.toMap(), dao.repayments.toMap())

        projector.reset()
        movementDao.movements.clear()
        script()

        assertEquals(first, listOf(movementDao.movements.toMap(), ledgerDao.entries.toMap(), ledgerDao.balances.toMap(), dao.loans.toMap(), dao.repayments.toMap()))
    }
}
