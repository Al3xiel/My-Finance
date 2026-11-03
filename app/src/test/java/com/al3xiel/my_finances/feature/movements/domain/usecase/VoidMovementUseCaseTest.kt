package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class VoidMovementUseCaseTest {
    private val f = MovementFixture()
    private val void = VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock)

    @Test
    fun emitsMovementVoidedWithTheTrimmedReasonDatedToday() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)

        assertEquals(Outcome.Ok(Unit), void(MovementId("m1"), " duplicado "))

        val stored = f.store.events.single()
        assertEquals(MovementVoided(MovementId("m1"), "duplicado", null), stored.event)
        assertEquals(f.today, stored.occurredOn)
    }

    @Test
    fun rejectsMissingReasonUnknownTransferAndAlreadyVoided() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)
        f.transferMovement("tr")
        f.incomeMovement("v", f.bbva, 100, status = MovementStatus.VOIDED)

        assertEquals(Outcome.Failed(BusinessError.ReasonRequired), void(MovementId("m1"), ""))
        assertEquals(Outcome.Failed(BusinessError.NotFound), void(MovementId("ghost"), "x"))
        assertEquals(Outcome.Failed(BusinessError.TransferNotEditable), void(MovementId("tr"), "x"))
        assertEquals(Outcome.Failed(BusinessError.AlreadyVoided), void(MovementId("v"), "x"))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun voidingAnIncomeThatLeavesTheBalanceNegativeWarnsAndSavesWhenConfirmed() = runTest {
        f.incomeMovement("m1", f.bbva, 1_500)

        assertEquals(Outcome.Failed(BusinessError.WouldGoNegative(-500)), void(MovementId("m1"), "duplicado"))
        assertEquals(0, f.store.events.size)

        assertEquals(Outcome.Ok(Unit), void(MovementId("m1"), "duplicado", confirmNegativeBalance = true))
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun voidingAnIncomeWithEnoughBalanceOrAnExpenseNeverWarns() = runTest {
        f.incomeMovement("inc", f.bbva, 1_000)
        f.expenseMovement("exp", f.bbva, "p1" to 5_000)

        assertEquals(Outcome.Ok(Unit), void(MovementId("inc"), "x"))
        assertEquals(Outcome.Ok(Unit), void(MovementId("exp"), "x"))
        assertEquals(2, f.store.events.size)
    }

    /**
     * Un repositorio que, como el real, ve anulado lo que ya se anuló. Lee el estado y recién después espera la
     * compuerta: así, sin candado, dos llamadas leen "activo" a la vez y ambas anulan.
     */
    private fun reflectingVoids(gate: CompletableDeferred<Unit>) = object : MovementRepository by f.movements {
        override suspend fun find(id: MovementId): Movement? {
            val movement = f.movements.movements[id]?.let { current ->
                val voided = f.store.events.any { (it.event as? MovementVoided)?.movementId == id }
                if (voided) current.copy(status = MovementStatus.VOIDED) else current
            }
            gate.await()
            return movement
        }
    }

    @Test
    fun twoConcurrentVoidsOfTheSameMovementEmitOnlyOneEvent() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)
        val gate = CompletableDeferred<Unit>()
        val useCase = VoidMovementUseCase(reflectingVoids(gate), f.accounts, f.rules, f.recorder, f.writeLock)

        val first = async { useCase(MovementId("m1"), "duplicado") }
        val second = async { useCase(MovementId("m1"), "duplicado") }
        runCurrent()
        gate.complete(Unit)
        val results = listOf(first.await(), second.await())

        assertEquals(1, f.store.events.size)
        assertEquals(1, results.count { it is Outcome.Ok })
        assertEquals(Outcome.Failed(BusinessError.AlreadyVoided), results.single { it is Outcome.Failed })
    }

    @Test
    fun withoutTheLockTheSameRaceWouldVoidTwice() = runTest {
        // Control: el mismo escenario con candados distintos demuestra que es el candado lo que evita el duplicado.
        f.expenseMovement("m1", f.bbva, "p1" to 100)
        val gate = CompletableDeferred<Unit>()
        val repository = reflectingVoids(gate)
        val a = VoidMovementUseCase(repository, f.accounts, f.rules, f.recorder, MovementWriteLock())
        val b = VoidMovementUseCase(repository, f.accounts, f.rules, f.recorder, MovementWriteLock())

        val first = async { a(MovementId("m1"), "duplicado") }
        val second = async { b(MovementId("m1"), "duplicado") }
        runCurrent()
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(2, f.store.events.size)
    }
}
