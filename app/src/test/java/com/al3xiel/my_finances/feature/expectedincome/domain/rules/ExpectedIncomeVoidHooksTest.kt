package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.FakeExpectedIncomeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ExpectedIncomeVoidHooksTest {
    private val repo = FakeExpectedIncomeRepository()
    private val hooks = ExpectedIncomeVoidHooks(repo)

    private val account = AccountId("bbva")
    private val source = IncomeSourceId("s1")
    private val m1 = MovementId("m1")

    /** `e1` confirmado con `m1` que generó `e2` en el estado dado. */
    private fun series(next: ExpectedIncomeStatus) {
        repo.put("e1", account, source, status = ExpectedIncomeStatus.CONFIRMED, confirmedMovementId = m1)
            .let { repo.items[it.id] = it.copy(nextExpectedId = ExpectedIncomeId("e2")) }
        repo.put("e2", account, source, status = next)
    }

    @Test
    fun aPendingNextIsCancelledWithTheStandardReason() = runTest {
        series(ExpectedIncomeStatus.SCHEDULED)

        assertEquals(
            Outcome.Ok(listOf(ExpectedIncomeCancelled(ExpectedIncomeId("e2"), ExpectedIncomeVoidHooks.CANCEL_REASON))),
            hooks.followUp(m1),
        )
    }

    @Test
    fun anOverdueNextIsPendingToo() = runTest {
        series(ExpectedIncomeStatus.OVERDUE)

        assertEquals(1, (hooks.followUp(m1) as Outcome.Ok).value.size)
    }

    @Test
    fun aConfirmedNextVetoesTheVoid() = runTest {
        series(ExpectedIncomeStatus.CONFIRMED)

        assertEquals(Outcome.Failed(BusinessError.NextExpectedConfirmed), hooks.followUp(m1))
    }

    @Test
    fun anAlreadyCancelledNextNeedsNothing() = runTest {
        series(ExpectedIncomeStatus.CANCELLED)

        assertEquals(Outcome.Ok(emptyList<Any>()), hooks.followUp(m1))
    }

    @Test
    fun noSeriesNoConsequences() = runTest {
        // Un ingreso que no confirmó nada.
        assertEquals(Outcome.Ok(emptyList<Any>()), hooks.followUp(MovementId("other")))

        // Confirmó un esperado que no repite.
        repo.put("e1", account, source, status = ExpectedIncomeStatus.CONFIRMED, confirmedMovementId = m1)
        assertEquals(Outcome.Ok(emptyList<Any>()), hooks.followUp(m1))
    }
}
