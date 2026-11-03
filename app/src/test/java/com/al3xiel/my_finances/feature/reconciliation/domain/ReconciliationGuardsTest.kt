package com.al3xiel.my_finances.feature.reconciliation.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.accounts.domain.usecase.ArchiveAccountUseCase
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ReconciliationGuardsTest {
    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()
    private val guards = ReconciliationGuards(reconciliations)

    private fun reconciliation(id: String, account: AccountId, status: ReconciliationStatus = ReconciliationStatus.OPEN) =
        Reconciliation(
            ReconciliationId(id), account, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN), status, null, null, null,
            Instant.EPOCH, null, false,
        ).also { reconciliations.reconciliations[it.id] = it }

    @Test
    fun linkingNeedsAnExistingOpenReconciliationOfTheMovementsAccount() = runTest {
        reconciliation("r1", f.bbva)
        reconciliation("r2", f.bbva, ReconciliationStatus.RESOLVED)

        assertNull(guards.check(ReconciliationId("r1"), setOf(f.bbva, f.cash)))
        assertEquals(BusinessError.NotFound, guards.check(ReconciliationId("zz"), setOf(f.bbva)))
        assertEquals(BusinessError.ReconciliationNotOpen, guards.check(ReconciliationId("r2"), setOf(f.bbva)))
        assertEquals(BusinessError.MovementNotInAccount, guards.check(ReconciliationId("r1"), setOf(f.cash)))
    }

    @Test
    fun anAccountWithAnOpenReconciliationIsNotArchived() = runTest {
        reconciliation("r1", f.bbva)
        reconciliation("r2", f.cash, ReconciliationStatus.RESOLVED)

        assertEquals(BusinessError.HasOpenReconciliation, guards.check(f.bbva))
        assertNull(guards.check(f.cash))
    }

    @Test
    fun archivingGoesThroughTheGuard() = runTest {
        reconciliation("r1", f.bbva)
        f.accounts.balances[f.bbva] = 0
        val archive = ArchiveAccountUseCase(f.accounts, f.recorder, setOf(guards))

        assertEquals(Outcome.Failed(BusinessError.HasOpenReconciliation), archive(f.bbva))
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun anAdjustmentIsNotCorrectedButItCanBeVoided() = runTest {
        val adjustment = f.incomeMovement("adj", f.bbva, 240).copy(type = MovementType.ADJUSTMENT)
        f.movements.movements[adjustment.id] = adjustment
        val correct = CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock)
        val void = VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock)

        val draft = MovementDraft.Income(f.bbva, f.pen(300), f.source, null, f.today)
        assertEquals(Outcome.Failed(BusinessError.AdjustmentLocked), correct(adjustment.id, draft, "monto"))
        assertTrue(f.store.events.isEmpty())

        assertEquals(Outcome.Ok(Unit), void(MovementId("adj"), "ya no corresponde", confirmNegativeBalance = true))
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun voidingAnUnidentifiedExpenseGivesTheMoneyBackAndNeverWarns() = runTest {
        val adjustment = f.expenseMovement("adj", f.bbva, "p" to 240L).copy(type = MovementType.ADJUSTMENT)
        f.movements.movements[adjustment.id] = adjustment
        val void = VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock)

        assertEquals(Outcome.Ok(Unit), void(MovementId("adj"), "ya no corresponde"))
    }
}
