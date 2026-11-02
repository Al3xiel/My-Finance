package com.al3xiel.my_finances.feature.funds.domain.usecase

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.FundToppedUp
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterIncomeWithTopUpUseCaseTest {
    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val register = RegisterIncomeWithTopUpUseCase(f.rules, funds, f.recorder, f.ids, f.writeLock)

    private val fare = FundId("fare")

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun recordsTheIncomeAndItsLinkedTopUpInOneCorrelation() = runTest {
        funds.put(fare)

        val result = register(f.bbva, f.pen(10_000), f.source, "Sueldo", f.today, fare, f.pen(5_000))

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        assertEquals(
            listOf(
                IncomeRegistered(MovementId("id-1"), f.bbva, f.pen(10_000), f.source, null, "Sueldo"),
                FundToppedUp(fare, f.pen(5_000), MovementId("id-1")),
            ),
            f.store.events.map { it.event },
        )
        assertEquals(1, f.store.events.map { it.correlationId }.toSet().size)
    }

    @Test
    fun theTopUpMayBeTheWholeIncomeButNotMore() = runTest {
        funds.put(fare)

        assertEquals(failed(BusinessError.ExceedsIncome), register(f.bbva, f.pen(10_000), f.source, null, f.today, fare, f.pen(10_001)))
        assertEquals(failed(BusinessError.InvalidAmount), register(f.bbva, f.pen(10_000), f.source, null, f.today, fare, f.pen(0)))
        assertTrue(f.store.events.isEmpty())

        assertEquals(Outcome.Ok(MovementId("id-1")), register(f.bbva, f.pen(10_000), f.source, null, f.today, fare, f.pen(10_000)))
    }

    @Test
    fun theIncomeItselfIsValidatedLikeAnyOther() = runTest {
        funds.put(fare)

        assertEquals(failed(BusinessError.InvalidAmount), register(f.bbva, f.pen(0), f.source, null, f.today, fare, f.pen(0)))
        assertEquals(failed(BusinessError.FutureDate), register(f.bbva, f.pen(100), f.source, null, f.today.plusDays(1), fare, f.pen(50)))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun theFundMustExistAndBeActive() = runTest {
        assertEquals(failed(BusinessError.NotFound), register(f.bbva, f.pen(100), f.source, null, f.today, fare, f.pen(50)))

        funds.put(fare, status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), register(f.bbva, f.pen(100), f.source, null, f.today, fare, f.pen(50)))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun waitsForTheSharedLockSoAClosingFundCannotBeToppedUpInBetween() = runTest(UnconfinedTestDispatcher()) {
        funds.put(fare)
        val gate = CompletableDeferred<Unit>()
        val holder = launch { f.writeLock.withLock { gate.await() } }

        val call = async { register(f.bbva, f.pen(10_000), f.source, null, f.today, fare, f.pen(5_000)) }
        assertTrue(call.isActive)
        assertTrue(f.store.events.isEmpty())

        // Mientras espera, el fondo se cierra: al entrar al candado ya lo ve cerrado y no recarga nada.
        funds.put(fare, status = FundStatus.CLOSED)
        gate.complete(Unit)

        assertEquals(failed(BusinessError.FundClosed), call.await())
        holder.join()
        assertTrue(f.store.events.isEmpty())
    }
}
