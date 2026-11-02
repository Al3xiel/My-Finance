package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.ExpectedIncomeCancelled
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.usecase.MovementVoidFollowUp
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoidFollowUpTest {
    private val f = MovementFixture()

    private fun voidWith(vararg followUps: MovementVoidFollowUp) =
        VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock, emptySet(), followUps.toSet())

    private val cancelNext = MovementVoidFollowUp { Outcome.Ok(listOf(ExpectedIncomeCancelled(ExpectedIncomeId("e2"), "motivo"))) }

    @Test
    fun theExtraEventsTravelInTheSameCorrelationRightAfterTheVoid() = runTest {
        f.incomeMovement("m1", f.cash, 0)

        val result = voidWith(cancelNext)(MovementId("m1"), "monto equivocado")

        assertEquals(Outcome.Ok(Unit), result)
        assertEquals(
            listOf(MovementVoided(MovementId("m1"), "monto equivocado", null), ExpectedIncomeCancelled(ExpectedIncomeId("e2"), "motivo")),
            f.store.events.map { it.event },
        )
        assertEquals(1, f.store.events.map { it.correlationId }.toSet().size)
        assertEquals(listOf(f.today, f.today), f.store.events.map { it.occurredOn })
    }

    @Test
    fun aFollowUpCanVetoTheVoidAndNothingIsSaved() = runTest {
        f.incomeMovement("m1", f.cash, 0)
        val veto = MovementVoidFollowUp { Outcome.Failed(BusinessError.NextExpectedConfirmed) }

        assertEquals(Outcome.Failed(BusinessError.NextExpectedConfirmed), voidWith(cancelNext, veto)(MovementId("m1"), "x"))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun withoutFollowUpsTheVoidIsAloneAsBefore() = runTest {
        f.incomeMovement("m1", f.cash, 0)

        voidWith()(MovementId("m1"), "x")

        assertEquals(listOf(MovementVoided(MovementId("m1"), "x", null)), f.store.events.map { it.event })
    }

    @Test
    fun followUpsAreNotAskedWhenTheVoidIsAlreadyRejected() = runTest {
        var asked = false
        val spy = MovementVoidFollowUp { asked = true; Outcome.Ok(emptyList()) }

        voidWith(spy)(MovementId("ghost"), "x")

        assertEquals(false, asked)
    }
}
