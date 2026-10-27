package com.al3xiel.my_finances.feature.loans.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.RepaymentId
import com.al3xiel.my_finances.domain.model.event.LoanGranted
import com.al3xiel.my_finances.domain.model.event.LoanRepaymentReceived
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.loans.domain.model.Loan
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.feature.loans.domain.repository.LoanRepository
import com.al3xiel.my_finances.feature.loans.domain.rules.LoanVoidGuard
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.people.domain.model.Person
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class LoanUseCasesTest {
    private val f = MovementFixture()
    private val loans = FakeLoanRepository()
    private val people = FakePersonRepository()
    private val grant = GrantLoanUseCase(people, f.accounts, f.rules, f.recorder, f.ids, f.writeLock)
    private val repay = ReceiveLoanRepaymentUseCase(loans, f.rules, f.recorder, f.ids, f.writeLock)

    private val pepe = PersonId("pepe")

    init {
        people.people[pepe] = Person(pepe, "Pepe", false)
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    private fun loan(principal: Long = 5_000, repaid: Long = 0) {
        loans.loans[LoanId("l1")] = Loan(
            LoanId("l1"), pepe, MovementId("ml1"), pen(principal), pen(repaid),
            if (repaid >= principal) LoanStatus.REPAID else LoanStatus.OPEN, LocalDate.of(2026, 10, 1),
        )
    }

    @Test
    fun grantingALoanEmitsLoanGrantedWithItsMovement() = runTest {
        f.accounts.balances[f.bbva] = 10_000

        val result = grant(pepe, f.bbva, pen(5_000), " taxi ", f.today)

        assertEquals(Outcome.Ok(LoanId("id-1")), result)
        assertEquals(LoanGranted(LoanId("id-1"), MovementId("id-2"), pepe, f.bbva, pen(5_000), "taxi"), f.store.events.single().event)
        assertEquals(f.today, f.store.events.single().occurredOn)
    }

    @Test
    fun grantingAboveTheBalanceWarnsAndSavesWhenConfirmed() = runTest {
        f.accounts.balances[f.bbva] = 1_000

        assertEquals(failed(BusinessError.WouldGoNegative(-4_000)), grant(pepe, f.bbva, pen(5_000), null, f.today))
        assertEquals(0, f.store.events.size)
        assertEquals(Outcome.Ok(LoanId("id-1")), grant(pepe, f.bbva, pen(5_000), null, f.today, confirmNegativeBalance = true))
    }

    @Test
    fun grantingRejectsUnknownOrArchivedPersonBadAmountAndFutureDate() = runTest {
        people.people[PersonId("old")] = Person(PersonId("old"), "Tío", true)

        assertEquals(failed(BusinessError.NotFound), grant(PersonId("ghost"), f.bbva, pen(100), null, f.today))
        assertEquals(failed(BusinessError.AlreadyArchived), grant(PersonId("old"), f.bbva, pen(100), null, f.today))
        assertEquals(failed(BusinessError.InvalidAmount), grant(pepe, f.bbva, pen(0), null, f.today))
        assertEquals(failed(BusinessError.FutureDate), grant(pepe, f.bbva, pen(100), null, f.today.plusDays(1)))
        assertEquals(failed(BusinessError.NotFound), grant(pepe, AccountId("ghost"), pen(100), null, f.today))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun aRepaymentAddsUpToThePendingAndNoMore() = runTest {
        loan(principal = 5_000, repaid = 3_000)

        assertEquals(Outcome.Ok(MovementId("id-2")), repay(LoanId("l1"), f.bbva, pen(2_000), f.today))
        assertEquals(
            LoanRepaymentReceived(RepaymentId("id-1"), MovementId("id-2"), LoanId("l1"), f.bbva, pen(2_000)),
            f.store.events.single().event,
        )
        assertEquals(failed(BusinessError.ExceedsPending), repay(LoanId("l1"), f.bbva, pen(2_001), f.today))
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun aRepaidOrUnknownLoanTakesNoMorePayments() = runTest {
        loan(principal = 5_000, repaid = 5_000)

        assertEquals(failed(BusinessError.ExceedsPending), repay(LoanId("l1"), f.bbva, pen(1), f.today))
        assertEquals(failed(BusinessError.NotFound), repay(LoanId("ghost"), f.bbva, pen(1), f.today))
        assertEquals(failed(BusinessError.InvalidAmount), repay(LoanId("l1"), f.bbva, pen(0), f.today))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun aLoanWithPaymentsCannotBeVoidedButOneWithoutPaymentsCan() = runTest {
        val guard = LoanVoidGuard(loans)

        loan(principal = 5_000, repaid = 0)
        assertNull(guard.check(MovementId("ml1")))

        loan(principal = 5_000, repaid = 2_000)
        assertEquals(BusinessError.HasRepayments, guard.check(MovementId("ml1")))
        assertNull(guard.check(MovementId("other")))
    }

    @Test
    fun grantingAndRepayingWaitForTheWriteLock() = runTest {
        loan(principal = 5_000, repaid = 0)
        f.accounts.balances[f.bbva] = 100_000
        val release = CompletableDeferred<Unit>()
        val holder = launch { f.writeLock.withLock { release.await() } }
        runCurrent()

        val granting = async { grant(pepe, f.bbva, pen(1_000), null, f.today) }
        val repaying = async { repay(LoanId("l1"), f.bbva, pen(1_000), f.today) }
        runCurrent()
        assertFalse(granting.isCompleted || repaying.isCompleted)
        assertEquals(0, f.store.events.size)

        release.complete(Unit)
        assertTrue(granting.await() is Outcome.Ok<*> && repaying.await() is Outcome.Ok<*>)
        holder.join()
        assertEquals(2, f.store.events.size)
    }
}

class FakeLoanRepository : LoanRepository {
    val loans = linkedMapOf<LoanId, Loan>()

    override suspend fun find(id: LoanId) = loans[id]
    override suspend fun findByMovement(movementId: MovementId) = loans.values.firstOrNull { it.movementId == movementId }
    override fun observeDetail(id: LoanId) = kotlinx.coroutines.flow.flowOf<com.al3xiel.my_finances.feature.loans.domain.model.LoanDetail?>(null)
}
