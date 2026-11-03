package com.al3xiel.my_finances.feature.movements.domain.usecase

import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.rules.FundExpenseGuard
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.model.MovementPart
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RegisterExpenseWithFundTest {
    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val guard = FundExpenseGuard(funds)
    private val register = RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids, f.writeLock, emptySet(), setOf(guard))
    private val correct = CorrectMovementUseCase(
        f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, emptySet(), emptySet(), setOf(guard),
    )

    private val fare = FundId("fare")

    private fun input(minor: Long, fund: FundId?, partId: PartId? = null, pool: PoolId? = null) =
        ExpensePartInput(partId, f.pen(minor), f.food, emptyList(), pool, fund)

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun theRegisteredPartCarriesItsFund() = runTest {
        funds.put(fare, startsOn = LocalDate.of(2026, 10, 1))

        val result = register(f.bbva, f.pen(150), listOf(input(100, fare), input(50, null)), null, f.today)

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        val parts = (f.store.events.single().event as ExpenseRegistered).parts
        assertEquals(listOf(fare, null), parts.map { it.fundId })
    }

    @Test
    fun rejectsAClosedFundAnOutOfRangeDateAndOneBeforeTheLastCount() = runTest {
        funds.put(fare, status = FundStatus.CLOSED)
        assertEquals(failed(BusinessError.FundClosed), register(f.bbva, f.pen(100), listOf(input(100, fare)), null, f.today))

        funds.put(fare, startsOn = LocalDate.of(2026, 10, 27))
        assertEquals(failed(BusinessError.DateOutOfFundRange), register(f.bbva, f.pen(100), listOf(input(100, fare)), null, f.today))

        funds.put(fare, lastCountOn = LocalDate.of(2026, 10, 26))
        assertEquals(
            failed(BusinessError.DateBeforeLastCount),
            register(f.bbva, f.pen(100), listOf(input(100, fare)), null, LocalDate.of(2026, 10, 25)),
        )
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun aMissingFundIsNotFound() = runTest {
        assertEquals(failed(BusinessError.NotFound), register(f.bbva, f.pen(100), listOf(input(100, fare)), null, f.today))
    }

    @Test
    fun aPartCannotHaveAPoolAndAFundAtTheSameTime() = runTest {
        funds.put(fare)

        val result = register(f.bbva, f.pen(100), listOf(input(100, fare, pool = PoolId("dogs"))), null, f.today)

        assertEquals(failed(BusinessError.InvalidPart), result)
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun spendingMoreThanTheRemainingDoesNotBlock() = runTest {
        funds.put(fare, allocated = 50)

        val result = register(f.bbva, f.pen(100), listOf(input(100, fare)), null, f.today)

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
    }

    @Test
    fun correctingAPartToAClosedFundIsRejectedButAnUnchangedPartPasses() = runTest {
        funds.put(fare, status = FundStatus.CLOSED)
        f.expenseMovement("m1", f.bbva, "p1" to 100L).let {
            f.movements.movements[it.id] = it.copy(parts = listOf(MovementPart(PartId("p1"), f.pen(100), f.food, com.al3xiel.my_finances.domain.model.PartKind.NORMAL, emptyList(), null, fare)))
        }

        val keep = MovementDraft.Expense(f.bbva, f.pen(80), listOf(input(80, fare, PartId("p1"))), null, f.today)
        assertEquals(Outcome.Ok(Unit), correct(MovementId("m1"), keep, "menos"))
        val snapshot = (f.store.events.single().event as MovementCorrected).snapshot as MovementSnapshot.Expense
        assertEquals(fare, snapshot.parts.single().fundId)

        val raise = MovementDraft.Expense(f.bbva, f.pen(120), listOf(input(120, fare, PartId("p1"))), null, f.today)
        assertEquals(failed(BusinessError.FundClosed), correct(MovementId("m1"), raise, "más"))
        val another = MovementDraft.Expense(f.bbva, f.pen(100), listOf(input(100, fare)), null, f.today)
        assertEquals(failed(BusinessError.FundClosed), correct(MovementId("m1"), another, "otra parte"))
    }

    @Test
    fun correctingAPartWithAPoolAndAFundIsRejected() = runTest {
        funds.put(fare)
        f.expenseMovement("m1", f.bbva, "p1" to 100L)

        val draft = MovementDraft.Expense(f.bbva, f.pen(100), listOf(input(100, fare, PartId("p1"), PoolId("dogs"))), null, f.today)

        assertEquals(failed(BusinessError.InvalidPart), correct(MovementId("m1"), draft, "ambos"))
    }

    @Test
    fun waitsForTheSharedLockSoACountingOrClosingFundCannotBeRacedWithANewExpense() = runTest(UnconfinedTestDispatcher()) {
        funds.put(fare)
        val gate = CompletableDeferred<Unit>()
        val holder = launch { f.writeLock.withLock { gate.await() } }

        val call = async { register(f.bbva, f.pen(100), listOf(input(100, fare)), null, f.today) }
        assertTrue(call.isActive)
        assertTrue(f.store.events.isEmpty())

        // El fondo se cierra mientras el gasto espera: ya no se acepta.
        funds.put(fare, status = FundStatus.CLOSED)
        gate.complete(Unit)

        assertEquals(failed(BusinessError.FundClosed), call.await())
        holder.join()
        assertTrue(f.store.events.isEmpty())
    }
}
