package com.al3xiel.my_finances.feature.reconciliation.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Los casos de uso de movimientos pasan `reconciliationId` al evento y lo validan con el gancho de la conciliación. */
class ReconciliationLinkedRegistrationTest {
    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()
    private val linkGuards = setOf(ReconciliationGuards(reconciliations))
    private val r1 = ReconciliationId("r1")

    private val income = RegisterIncomeUseCase(f.rules, f.recorder, f.ids, linkGuards)
    private val expense = RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids, f.writeLock, emptySet(), emptySet(), linkGuards)
    private val transfer = RegisterTransferUseCase(f.rules, f.accounts, f.recorder, f.ids, linkGuards)
    private val correct = CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, emptySet(), emptySet(), emptySet(), linkGuards)

    init {
        reconciliations.reconciliations[r1] = Reconciliation(
            r1, f.bbva, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN), ReconciliationStatus.OPEN, null, null, null,
            Instant.EPOCH, null, false,
        )
    }

    @Test
    fun anIncomeRegisteredFromTheReconciliationCarriesItsId() = runTest {
        assertTrue(income(f.bbva, f.pen(1_000), f.source, "reembolso", f.today, r1) is Outcome.Ok)

        assertEquals(r1, (f.store.events.single().event as IncomeRegistered).reconciliationId)
    }

    @Test
    fun anExpenseAndATransferCarryItToo() = runTest {
        assertTrue(expense(f.bbva, f.pen(100), listOf(f.input(100)), null, f.today, reconciliationId = r1) is Outcome.Ok)
        assertTrue(transfer(f.bbva, f.cash, f.pen(50), null, null, f.today, reconciliationId = r1) is Outcome.Ok)

        assertEquals(r1, (f.store.events[0].event as ExpenseRegistered).reconciliationId)
        assertEquals(r1, (f.store.events[1].event as TransferRegistered).reconciliationId)
    }

    @Test
    fun withoutAReconciliationTheIdStaysNull() = runTest {
        income(f.bbva, f.pen(1_000), f.source, null, f.today)

        assertNull((f.store.events.single().event as IncomeRegistered).reconciliationId)
    }

    @Test
    fun aMovementOfAnotherAccountOrAClosedReconciliationIsRejectedAndNothingIsSaved() = runTest {
        assertEquals(
            Outcome.Failed(BusinessError.MovementNotInAccount),
            income(f.cash, f.pen(1_000), f.source, null, f.today, r1),
        )
        assertEquals(
            Outcome.Failed(BusinessError.NotFound),
            income(f.bbva, f.pen(1_000), f.source, null, f.today, ReconciliationId("zz")),
        )
        reconciliations.reconciliations[r1] = reconciliations.reconciliations.getValue(r1).copy(status = ReconciliationStatus.CANCELLED)
        assertEquals(
            Outcome.Failed(BusinessError.ReconciliationNotOpen),
            expense(f.bbva, f.pen(100), listOf(f.input(100)), null, f.today, reconciliationId = r1),
        )
        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun correctingCanBeLinkedToo() = runTest {
        f.incomeMovement("m1", f.bbva, 500)

        val draft = MovementDraft.Income(f.bbva, f.pen(700), f.source, null, f.today)
        assertEquals(Outcome.Ok(Unit), correct(MovementId("m1"), draft, "monto", reconciliationId = r1))

        assertEquals(r1, (f.store.events.single().event as MovementCorrected).reconciliationId)
    }
}
