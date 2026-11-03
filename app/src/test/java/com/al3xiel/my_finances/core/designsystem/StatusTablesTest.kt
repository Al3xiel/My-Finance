package com.al3xiel.my_finances.core.designsystem

import com.al3xiel.my_finances.domain.model.ReimbursementStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.ui.badge
import com.al3xiel.my_finances.feature.movements.ui.components.badge
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import com.al3xiel.my_finances.feature.pools.ui.components.badge
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.ui.components.badge
import org.junit.Assert.assertEquals
import org.junit.Test

/** La tabla de estados de docs/05 §7: una insignia por estado; si un estado cambia de grupo, este test lo dice. */
class StatusTablesTest {
    @Test fun expectedIncome() {
        assertEquals(BadgeStatus.PENDING, ExpectedIncomeStatus.SCHEDULED.badge())
        assertEquals(BadgeStatus.PENDING, ExpectedIncomeStatus.CANCELLED.badge())
        assertEquals(BadgeStatus.OVERDUE, ExpectedIncomeStatus.OVERDUE.badge())
        assertEquals(BadgeStatus.RECONCILED, ExpectedIncomeStatus.CONFIRMED.badge())
    }

    @Test fun poolPeriod() {
        assertEquals(BadgeStatus.PENDING, PoolPeriodStatus.OPEN.badge())
        assertEquals(BadgeStatus.PENDING, PoolPeriodStatus.STATEMENT_SENT.badge())
        assertEquals(BadgeStatus.RECONCILED, PoolPeriodStatus.SETTLED.badge())
    }

    @Test fun reconciliation() {
        assertEquals(BadgeStatus.PENDING, ReconciliationStatus.OPEN.badge())
        assertEquals(BadgeStatus.PENDING, ReconciliationStatus.CANCELLED.badge())
        assertEquals(BadgeStatus.RECONCILED, ReconciliationStatus.RESOLVED.badge())
    }

    @Test fun reimbursement() {
        assertEquals(BadgeStatus.PENDING, ReimbursementStatus.PENDING.badge())
        assertEquals(BadgeStatus.PENDING, ReimbursementStatus.PARTIALLY_REIMBURSED.badge())
        assertEquals(BadgeStatus.RECONCILED, ReimbursementStatus.REIMBURSED.badge())
    }
}
