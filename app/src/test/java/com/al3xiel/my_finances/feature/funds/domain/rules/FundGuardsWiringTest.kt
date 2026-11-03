package com.al3xiel.my_finances.feature.funds.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.usecase.FundReferenceGuard
import com.al3xiel.my_finances.domain.usecase.MovementCorrectionGuard
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.feature.funds.di.FundsModule
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import dagger.multibindings.IntoSet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los ganchos de fondos tienen que llegar a los casos de uso reales: probados solos no bastan. */
class FundGuardsWiringTest {
    private val f = MovementFixture()
    private val funds = FakeFundRepository()
    private val guards = FundMovementGuards(funds, f.movements)
    private val void = VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock, setOf(guards))
    private val correct = CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, setOf(guards))

    private val income = MovementId("income1")

    @Test
    fun anIncomeWithTopUpsIsNeitherVoidedNorLoweredBelowThem() = runTest {
        f.incomeMovement("income1", f.bbva, 10_000)
        funds.toppedUp[income] = 5_000

        assertEquals(Outcome.Failed(BusinessError.HasTopUps), void(income, "error"))
        val lower = MovementDraft.Income(f.bbva, f.pen(4_000), f.source, null, f.today)
        assertEquals(Outcome.Failed(BusinessError.BelowToppedUpAmount), correct(income, lower, "menos"))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun anIncomeWhoseMoneyIsSetAsideIsNotVoided() = runTest {
        f.incomeMovement("income1", f.bbva, 10_000)
        funds.available = 2_000

        assertEquals(Outcome.Failed(BusinessError.ReservedByFunds), void(income, "error"))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun theUntrackedSpendOfACountIsNeitherVoidedNorCorrectedAndSaysSo() = runTest {
        val untracked = f.expenseMovement("m9", f.bbva, "m9-untracked" to 500L).copy(type = MovementType.FUND_UNTRACKED_SPEND)
        f.movements.movements[untracked.id] = untracked

        assertEquals(Outcome.Failed(BusinessError.UntrackedSpendLocked), void(untracked.id, "error"))
        val draft = MovementDraft.Expense(f.bbva, f.pen(500), emptyList(), null, f.today)
        assertEquals(Outcome.Failed(BusinessError.UntrackedSpendLocked), correct(untracked.id, draft, "error"))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun theHiltModuleContributesTheGuardsToTheSetsTheUseCasesConsume() {
        val contributed = FundsModule::class.java.declaredMethods.filter { it.isAnnotationPresent(IntoSet::class.java) }.map { it.returnType }

        assertTrue(MovementVoidGuard::class.java in contributed)
        assertTrue(MovementCorrectionGuard::class.java in contributed)
        assertTrue(FundReferenceGuard::class.java in contributed)
    }
}
