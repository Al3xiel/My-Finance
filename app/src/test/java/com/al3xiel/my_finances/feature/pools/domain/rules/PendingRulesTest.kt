package com.al3xiel.my_finances.feature.pools.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingRulesTest {
    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    @Test
    fun pendingIsAmountMinusReimbursed() {
        assertEquals(pen(30), PendingRules.pending(pen(100), pen(70)))
    }

    @Test
    fun partStatusFollowsTheReimbursedAmount() {
        assertEquals(ReimbursementStatus.PENDING, PendingRules.statusOf(pen(100), pen(0)))
        assertEquals(ReimbursementStatus.PARTIALLY_REIMBURSED, PendingRules.statusOf(pen(100), pen(40)))
        assertEquals(ReimbursementStatus.REIMBURSED, PendingRules.statusOf(pen(100), pen(100)))
    }

    @Test
    fun periodIsOpenUntilTheStatementIsSent() {
        assertEquals(PoolPeriodStatus.OPEN, PendingRules.periodStatus(statementSent = false, pending = pen(0)))
        assertEquals(PoolPeriodStatus.OPEN, PendingRules.periodStatus(statementSent = false, pending = pen(50)))
    }

    @Test
    fun periodAfterStatementIsSentUntilNothingIsPending() {
        assertEquals(PoolPeriodStatus.STATEMENT_SENT, PendingRules.periodStatus(statementSent = true, pending = pen(50)))
        assertEquals(PoolPeriodStatus.SETTLED, PendingRules.periodStatus(statementSent = true, pending = pen(0)))
    }
}
