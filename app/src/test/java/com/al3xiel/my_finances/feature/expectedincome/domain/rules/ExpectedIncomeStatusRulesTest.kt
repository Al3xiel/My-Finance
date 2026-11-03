package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpectedIncomeStatusRulesTest {
    private val to = LocalDate.of(2026, 11, 5)

    @Test
    fun overdueOnlyTheDayAfterTheRangeEnds() {
        assertFalse(ExpectedIncomeStatusRules.isOverdue(to, today = to.minusDays(1)))
        assertFalse(ExpectedIncomeStatusRules.isOverdue(to, today = to))
        assertTrue(ExpectedIncomeStatusRules.isOverdue(to, today = to.plusDays(1)))
    }

    @Test
    fun theEffectiveStatusUsesTheSameRule() {
        for (offset in -2L..2L) {
            val today = to.plusDays(offset)
            val expected = if (ExpectedIncomeStatusRules.isOverdue(to, today)) ExpectedIncomeStatus.OVERDUE else ExpectedIncomeStatus.SCHEDULED
            assertEquals(expected, ExpectedIncomeRules.effectiveStatus(ExpectedIncomeStatus.SCHEDULED, to, today))
        }
    }
}
