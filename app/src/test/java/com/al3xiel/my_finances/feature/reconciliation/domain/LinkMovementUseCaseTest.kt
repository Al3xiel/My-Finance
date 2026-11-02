package com.al3xiel.my_finances.feature.reconciliation.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.MovementLinkedToReconciliation
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.LinkMovementUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LinkMovementUseCaseTest {
    private val f = MovementFixture()
    private val reconciliations = FakeReconciliationRepository()
    private val link = LinkMovementUseCase(reconciliations, f.movements, f.recorder)
    private val r1 = ReconciliationId("r1")

    private fun open(account: AccountId = f.bbva, status: ReconciliationStatus = ReconciliationStatus.OPEN) =
        Reconciliation(r1, account, Money(0, CurrencyCode.PEN), Money(0, CurrencyCode.PEN), status, null, null, null, Instant.EPOCH, null, false)
            .also { reconciliations.reconciliations[r1] = it }

    @Test
    fun linksAnExistingMovementOfTheAccount() = runTest {
        open()
        f.incomeMovement("m1", f.bbva, 1_000)

        assertEquals(Outcome.Ok(Unit), link(r1, MovementId("m1")))

        assertEquals(MovementLinkedToReconciliation(r1, MovementId("m1")), f.store.events.single().event)
    }

    @Test
    fun aTransferLinksFromEitherAccount() = runTest {
        open(account = f.cash)
        f.transferMovement("t1")

        assertEquals(Outcome.Ok(Unit), link(r1, MovementId("t1")))
    }

    @Test
    fun linkingTwiceRecordsNothingNew() = runTest {
        open()
        f.incomeMovement("m1", f.bbva, 1_000)
        reconciliations.linked += r1 to MovementId("m1")

        assertEquals(Outcome.Ok(Unit), link(r1, MovementId("m1")))

        assertTrue(f.store.events.isEmpty())
    }

    @Test
    fun rejectsUnknownVoidedForeignOrNotOpenCases() = runTest {
        open()
        assertEquals(Outcome.Failed(BusinessError.NotFound), link(r1, MovementId("nope")))

        f.incomeMovement("v1", f.bbva, 100, status = MovementStatus.VOIDED)
        assertEquals(Outcome.Failed(BusinessError.AlreadyVoided), link(r1, MovementId("v1")))

        f.incomeMovement("c1", f.cash, 100)
        assertEquals(Outcome.Failed(BusinessError.MovementNotInAccount), link(r1, MovementId("c1")))

        open(status = ReconciliationStatus.RESOLVED)
        f.incomeMovement("m1", f.bbva, 100)
        assertEquals(Outcome.Failed(BusinessError.ReconciliationNotOpen), link(r1, MovementId("m1")))

        assertEquals(Outcome.Failed(BusinessError.NotFound), link(ReconciliationId("zz"), MovementId("m1")))
        assertTrue(f.store.events.isEmpty())
    }
}
