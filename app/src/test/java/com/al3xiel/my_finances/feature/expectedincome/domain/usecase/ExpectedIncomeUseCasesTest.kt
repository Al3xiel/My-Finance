package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeEdited
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeScheduled
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpectedIncomeUseCasesTest {
    private val f = MovementFixture()
    private val repo = FakeExpectedIncomeRepository()
    private val schedule = ScheduleExpectedIncomeUseCase(f.rules, f.recorder, f.ids)
    private val edit = EditExpectedIncomeUseCase(f.rules, repo, f.recorder, f.writeLock)
    private val cancel = CancelExpectedIncomeUseCase(repo, f.recorder, f.writeLock)

    private val e1 = ExpectedIncomeId("e1")
    private val range = ExpectedRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 5))

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun scheduleRecordsTheEventWithAGeneratedId() = runTest {
        val result = schedule(f.bbva, f.source, f.pen(200_000), range, true)

        assertEquals(Outcome.Ok(ExpectedIncomeId("id-1")), result)
        assertEquals(
            listOf(ExpectedIncomeScheduled(ExpectedIncomeId("id-1"), f.source, f.bbva, f.pen(200_000), range.from, range.to, true)),
            f.store.events.map { it.event },
        )
    }

    @Test
    fun aPastRangeIsAllowedButNotAnInvertedOne() = runTest {
        val past = ExpectedRange(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 5))
        assertEquals(Outcome.Ok(ExpectedIncomeId("id-1")), schedule(f.bbva, f.source, f.pen(100), past, false))

        assertEquals(failed(BusinessError.InvalidRange), schedule(f.bbva, f.source, f.pen(100), ExpectedRange(past.to, past.from), false))
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun scheduleValidatesAmountAccountAndSource() = runTest {
        assertEquals(failed(BusinessError.InvalidAmount), schedule(f.bbva, f.source, f.pen(0), range, false))
        assertEquals(failed(BusinessError.NotFound), schedule(com.al3xiel.my_finances.domain.model.AccountId("x"), f.source, f.pen(100), range, false))
        assertEquals(failed(BusinessError.NotFound), schedule(f.bbva, IncomeSourceId("x"), f.pen(100), range, false))
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        assertEquals(failed(BusinessError.AlreadyArchived), schedule(f.cash, f.source, f.pen(100), range, false))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun editSendsOnlyWhatChanged() = runTest {
        repo.put("e1", f.bbva, f.source)

        val result = edit(e1, ExpectedIncomeChanges(accountId = f.cash, estimated = f.pen(200_000), to = LocalDate.of(2026, 11, 6)))

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals(
            listOf(ExpectedIncomeEdited(e1, null, f.cash, null, null, LocalDate.of(2026, 11, 6), null)),
            f.store.events.map { it.event },
        )
    }

    @Test
    fun editWithoutRealChangesRecordsNothing() = runTest {
        repo.put("e1", f.bbva, f.source)

        assertEquals(Outcome.Ok(Unit), edit(e1, ExpectedIncomeChanges(accountId = f.bbva, estimated = f.pen(200_000))))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun editValidatesTheMergedRange() = runTest {
        repo.put("e1", f.bbva, f.source)

        assertEquals(failed(BusinessError.InvalidRange), edit(e1, ExpectedIncomeChanges(to = LocalDate.of(2026, 10, 1))))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun editAnArchivedAccountThatIsNotTouchedStillWorks() = runTest {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        repo.put("e1", f.cash, f.source)

        assertEquals(Outcome.Ok(Unit), edit(e1, ExpectedIncomeChanges(estimated = f.pen(5_000))))
    }

    @Test
    fun editCannotMoveItToAnArchivedAccount() = runTest {
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        repo.put("e1", f.bbva, f.source)

        assertEquals(failed(BusinessError.AlreadyArchived), edit(e1, ExpectedIncomeChanges(accountId = f.cash)))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun editAndCancelOnlyApplyToPendingOnes() = runTest {
        repo.put("e1", f.bbva, f.source, status = ExpectedIncomeStatus.CONFIRMED)
        repo.put("e2", f.bbva, f.source, status = ExpectedIncomeStatus.CANCELLED)
        repo.put("e3", f.bbva, f.source, status = ExpectedIncomeStatus.OVERDUE)

        assertEquals(failed(BusinessError.NotPending), edit(e1, ExpectedIncomeChanges(estimated = f.pen(1))))
        assertEquals(failed(BusinessError.NotPending), cancel(ExpectedIncomeId("e2"), null))
        assertEquals(failed(BusinessError.NotFound), cancel(ExpectedIncomeId("zz"), null))
        assertTrue(f.store.events.isEmpty())

        assertEquals(Outcome.Ok(Unit), edit(ExpectedIncomeId("e3"), ExpectedIncomeChanges(estimated = f.pen(1))))
    }

    @Test
    fun cancelKeepsATrimmedReasonOrNone() = runTest {
        repo.put("e1", f.bbva, f.source)
        repo.put("e2", f.bbva, f.source)

        cancel(e1, "  ya no cobro ahí ")
        cancel(ExpectedIncomeId("e2"), "   ")

        assertEquals(
            listOf(ExpectedIncomeCancelled(e1, "ya no cobro ahí"), ExpectedIncomeCancelled(ExpectedIncomeId("e2"), null)),
            f.store.events.map { it.event },
        )
    }
}
