package com.al3xiel.my_finances.feature.funds.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FundMovementGuardsTest {
    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val guards = FundMovementGuards(funds, f.movements)

    private val income = MovementId("income1")

    private fun incomeSnapshot(minor: Long) = MovementSnapshot.Income(f.bbva, f.pen(minor), f.source, null)

    @Test
    fun anIncomeWithoutTopUpsCanBeVoidedAndLoweredFreely() = runTest {
        f.incomeMovement("income1", f.bbva, 10_000)

        assertNull(guards.check(income))
        assertNull(guards.check(income, incomeSnapshot(1)))
    }

    @Test
    fun anIncomeWithTopUpsIsNotVoided() = runTest {
        f.incomeMovement("income1", f.bbva, 10_000)
        funds.toppedUp[income] = 5_000

        assertEquals(BusinessError.HasTopUps, guards.check(income))
    }

    @Test
    fun anIncomeWithTopUpsCannotGoBelowWhatWasToppedUp() = runTest {
        f.incomeMovement("income1", f.bbva, 10_000)
        funds.toppedUp[income] = 5_000

        assertEquals(BusinessError.BelowToppedUpAmount, guards.check(income, incomeSnapshot(4_999)))
        assertNull(guards.check(income, incomeSnapshot(5_000)))
        assertNull(guards.check(income, incomeSnapshot(20_000)))
    }

    @Test
    fun untrackedSpendIsNeitherVoidedNorCorrected() = runTest {
        f.expenseMovement("m9", f.bbva, "m9-untracked" to 500L)
        f.movements.movements[MovementId("m9")] = f.movements.movements.getValue(MovementId("m9")).copy(type = MovementType.FUND_UNTRACKED_SPEND)
        val snapshot = MovementSnapshot.Expense(f.bbva, f.pen(500), emptyList(), null)

        assertEquals(BusinessError.UntrackedSpendLocked, guards.check(MovementId("m9")))
        assertEquals(BusinessError.UntrackedSpendLocked, guards.check(MovementId("m9"), snapshot))
    }

    @Test
    fun expensesAndMissingMovementsAreNotTheirBusiness() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100L)

        assertNull(guards.check(MovementId("m1")))
        assertNull(guards.check(MovementId("ghost")))
    }

    @Test
    fun voidingAnIncomeWhoseMoneyIsAlreadySetAsideIsRejected() = runTest {
        f.incomeMovement("income1", f.bbva, 10_000)

        funds.available = 9_999
        assertEquals(BusinessError.ReservedByFunds, guards.check(income))
        funds.available = 10_000
        assertNull(guards.check(income))
    }

    @Test
    fun loweringAnIncomeIsRejectedOnlyWhenWhatIsFreedIsNotAvailable() = runTest {
        f.incomeMovement("income1", f.bbva, 10_000)

        funds.available = 3_999
        assertEquals(BusinessError.ReservedByFunds, guards.check(income, incomeSnapshot(6_000)))
        funds.available = 4_000
        assertNull(guards.check(income, incomeSnapshot(6_000)))
        funds.available = 0
        assertNull(guards.check(income, incomeSnapshot(10_000)))
        assertNull(guards.check(income, incomeSnapshot(12_000)))
    }
}
