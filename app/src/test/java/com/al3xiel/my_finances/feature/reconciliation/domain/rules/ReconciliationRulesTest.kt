package com.al3xiel.my_finances.feature.reconciliation.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

class ReconciliationRulesTest {
    @Test
    fun computedBalanceAddsTheCutAccountEntriesAndTheLinkedOnes() {
        assertEquals(pen(79_500), ReconciliationRules.computedBalance(pen(80_000), pen(-500)))
    }

    @Test
    fun remainingDifferenceIsRealMinusComputed() {
        assertEquals(pen(1_240), ReconciliationRules.remainingDifference(pen(81_240), pen(80_000)))
        assertEquals(pen(-1_500), ReconciliationRules.remainingDifference(pen(-1_500), pen(0)))
        assertEquals(pen(0), ReconciliationRules.remainingDifference(pen(0), pen(0)))
    }

    @Test
    fun zeroDifferenceAdmitsOnlyTheCausesThatNeedNoAdjustment() {
        assertEquals(
            setOf(ReconciliationCause.BALANCED, ReconciliationCause.MISSING_RECORD, ReconciliationCause.RECORDING_ERROR),
            ReconciliationRules.allowedCauses(pen(0)),
        )
    }

    @Test
    fun nonZeroDifferenceAdmitsOnlyUnknownOrSuspectedAppError() {
        val expected = setOf(ReconciliationCause.UNKNOWN, ReconciliationCause.SUSPECTED_APP_ERROR)
        assertEquals(expected, ReconciliationRules.allowedCauses(pen(240)))
        assertEquals(expected, ReconciliationRules.allowedCauses(pen(-240)))
        assertTrue(ReconciliationRules.isCauseAllowed(ReconciliationCause.UNKNOWN, pen(1)))
        assertFalse(ReconciliationRules.isCauseAllowed(ReconciliationCause.BALANCED, pen(1)))
        assertFalse(ReconciliationRules.isCauseAllowed(ReconciliationCause.UNKNOWN, pen(0)))
    }

    @Test
    fun adjustmentDirectionComesFromTheSignAndAmountIsAbsolute() {
        val income = ReconciliationRules.adjustmentFor(pen(240))!!
        assertEquals(AdjustmentDirection.UNIDENTIFIED_INCOME, income.direction)
        assertEquals(pen(240), income.amount)

        val expense = ReconciliationRules.adjustmentFor(pen(-1_500))!!
        assertEquals(AdjustmentDirection.UNIDENTIFIED_EXPENSE, expense.direction)
        assertEquals(pen(1_500), expense.amount)
    }

    private fun reconciliation(status: ReconciliationStatus) = Reconciliation(
        ReconciliationId("r1"), AccountId("bbva"), pen(0), pen(0), status, null, null, null, Instant.EPOCH, null, false,
    )

    @Test
    fun linkingNeedsAnExistingOpenReconciliationOfAnAccountTheMovementTouches() {
        val touched = setOf(AccountId("bbva"), AccountId("cash"))

        assertNull(ReconciliationRules.linkError(reconciliation(ReconciliationStatus.OPEN), touched))
        assertEquals(BusinessError.NotFound, ReconciliationRules.linkError(null, touched))
        assertEquals(BusinessError.ReconciliationNotOpen, ReconciliationRules.linkError(reconciliation(ReconciliationStatus.RESOLVED), touched))
        assertEquals(BusinessError.ReconciliationNotOpen, ReconciliationRules.linkError(reconciliation(ReconciliationStatus.CANCELLED), touched))
        assertEquals(BusinessError.MovementNotInAccount, ReconciliationRules.linkError(reconciliation(ReconciliationStatus.OPEN), setOf(AccountId("cash"))))
    }

    @Test
    fun zeroDifferenceHasNoAdjustment() {
        assertNull(ReconciliationRules.adjustmentFor(pen(0)))
    }
}
