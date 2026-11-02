package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus.CANCELLED
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus.CONFIRMED
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus.OVERDUE
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus.SCHEDULED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpectedIncomeRulesTest {
    private val to = LocalDate.of(2026, 11, 5)

    @Test
    fun rangeMustNotEndBeforeItStarts() {
        assertEquals(BusinessError.InvalidRange, ExpectedIncomeRules.validateRange(ExpectedRange(to, to.minusDays(1))))
        assertNull(ExpectedIncomeRules.validateRange(ExpectedRange(to, to)))
    }

    @Test
    fun aPastRangeIsAllowed() {
        assertNull(ExpectedIncomeRules.validateRange(ExpectedRange(LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 5))))
    }

    @Test
    fun scheduledBecomesOverdueOnlyAfterTheEnd() {
        assertEquals(SCHEDULED, ExpectedIncomeRules.effectiveStatus(SCHEDULED, to, to))
        assertEquals(OVERDUE, ExpectedIncomeRules.effectiveStatus(SCHEDULED, to, to.plusDays(1)))
    }

    @Test
    fun confirmedAndCancelledAreTerminal() {
        assertEquals(CONFIRMED, ExpectedIncomeRules.effectiveStatus(CONFIRMED, to, to.plusDays(30)))
        assertEquals(CANCELLED, ExpectedIncomeRules.effectiveStatus(CANCELLED, to, to.plusDays(30), confirmedMovementVoided = true))
    }

    @Test
    fun confirmedWithVoidedMovementGoesBackToPending() {
        assertEquals(SCHEDULED, ExpectedIncomeRules.effectiveStatus(CONFIRMED, to, to.minusDays(1), confirmedMovementVoided = true))
        assertEquals(OVERDUE, ExpectedIncomeRules.effectiveStatus(CONFIRMED, to, to.plusDays(1), confirmedMovementVoided = true))
    }

    @Test
    fun onlyScheduledAndOverdueArePending() {
        assertTrue(ExpectedIncomeRules.isPending(SCHEDULED))
        assertTrue(ExpectedIncomeRules.isPending(OVERDUE))
        assertFalse(ExpectedIncomeRules.isPending(CONFIRMED))
        assertFalse(ExpectedIncomeRules.isPending(CANCELLED))
    }
}
