package com.al3xiel.my_finances.feature.reconciliation.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AdjustmentData
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.model.event.ReconciliationResolved
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.ResolveReconciliationUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ResolveReconciliationUseCaseTest {
    private val store = FakeEventStore()
    private val reconciliations = FakeReconciliationRepository()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val resolve = ResolveReconciliationUseCase(reconciliations, EventRecorder(store, ids, clock), ids)
    private val r1 = ReconciliationId("r1")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun open(difference: Long, status: ReconciliationStatus = ReconciliationStatus.OPEN) {
        reconciliations.reconciliations[r1] = Reconciliation(
            r1, AccountId("bbva"), pen(0), pen(0), status, null, null, null, Instant.EPOCH, null, false,
        )
        reconciliations.differences[r1] = pen(difference)
    }

    private fun resolved(): ReconciliationResolved = store.events.single().event as ReconciliationResolved

    @Test
    fun aPositiveDifferenceCreatesAnUnidentifiedIncomeForItsExactAmount() = runTest {
        open(240)

        assertEquals(Outcome.Ok(Unit), resolve(r1, ReconciliationCause.UNKNOWN, "no encontré el origen"))

        val adjustment = resolved().adjustment!!
        assertEquals(AdjustmentDirection.UNIDENTIFIED_INCOME, adjustment.direction)
        assertEquals(pen(240), adjustment.amount)
        assertEquals("no encontré el origen", resolved().note)
        assertEquals(LocalDate.of(2026, 11, 2), store.events.single().occurredOn)
    }

    @Test
    fun aNegativeDifferenceCreatesAnUnidentifiedExpenseByItsAbsoluteValue() = runTest {
        open(-1_500)

        resolve(r1, ReconciliationCause.SUSPECTED_APP_ERROR, null)

        assertEquals(AdjustmentData(resolved().adjustment!!.movementId, AdjustmentDirection.UNIDENTIFIED_EXPENSE, pen(1_500)), resolved().adjustment)
    }

    @Test
    fun theAdjustmentMovementGetsItsOwnIdFromTheGenerator() = runTest {
        open(240)

        resolve(r1, ReconciliationCause.UNKNOWN, null)

        val id = resolved().adjustment!!.movementId
        assertTrue(id.value.isNotBlank())
        assertEquals(MovementId(id.value), id)
    }

    @Test
    fun zeroDifferenceResolvesWithoutAnAdjustmentAndDropsABlankNote() = runTest {
        open(0)

        assertEquals(Outcome.Ok(Unit), resolve(r1, ReconciliationCause.MISSING_RECORD, "  "))

        assertEquals(ReconciliationResolved(r1, ReconciliationCause.MISSING_RECORD, null, null), resolved())
    }

    @Test
    fun theCauseMustMatchTheDifference() = runTest {
        open(0)
        assertEquals(Outcome.Failed(BusinessError.CauseNotAllowed), resolve(r1, ReconciliationCause.UNKNOWN, null))
        open(240)
        assertEquals(Outcome.Failed(BusinessError.CauseNotAllowed), resolve(r1, ReconciliationCause.BALANCED, null))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun onlyAnExistingOpenReconciliationIsResolved() = runTest {
        assertEquals(Outcome.Failed(BusinessError.NotFound), resolve(r1, ReconciliationCause.BALANCED, null))
        open(0, ReconciliationStatus.CANCELLED)
        assertEquals(Outcome.Failed(BusinessError.ReconciliationNotOpen), resolve(r1, ReconciliationCause.BALANCED, null))
        assertTrue(store.events.isEmpty())
    }
}
