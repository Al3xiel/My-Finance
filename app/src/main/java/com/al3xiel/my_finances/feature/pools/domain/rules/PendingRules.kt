package com.al3xiel.my_finances.feature.pools.domain.rules

import com.al3xiel.my_finances.core.money.Money

enum class ReimbursementStatus { PENDING, PARTIALLY_REIMBURSED, REIMBURSED }

enum class PoolPeriodStatus { OPEN, STATEMENT_SENT, SETTLED }

/** Pendiente y estados derivados; no son eventos (un hecho derivable no se guarda). */
object PendingRules {
    fun pending(amount: Money, reimbursed: Money): Money = amount - reimbursed

    fun statusOf(amount: Money, reimbursed: Money): ReimbursementStatus = when {
        reimbursed.amountMinor <= 0L -> ReimbursementStatus.PENDING
        reimbursed.amountMinor >= amount.amountMinor -> ReimbursementStatus.REIMBURSED
        else -> ReimbursementStatus.PARTIALLY_REIMBURSED
    }

    /** `OPEN` hasta rendir; tras rendir, `STATEMENT_SENT` y `SETTLED` cuando ya no queda pendiente. */
    fun periodStatus(statementSent: Boolean, pending: Money): PoolPeriodStatus = when {
        !statementSent -> PoolPeriodStatus.OPEN
        pending.amountMinor > 0L -> PoolPeriodStatus.STATEMENT_SENT
        else -> PoolPeriodStatus.SETTLED
    }
}
