package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingRules
import javax.inject.Inject

/**
 * Totales y estado de cada periodo como función de las partes que tiene asignadas y de si ya se rindió. Lo llaman el
 * proyector de cúmulos y el de reembolsos después de cambiar lo suyo: así el resultado final no depende del orden en
 * que corran los proyectores durante un replay.
 */
class PoolPeriodRefresher @Inject constructor(private val pools: PoolDao) {
    suspend fun refresh() {
        val sums = pools.periodSums().associateBy { it.periodId }
        pools.allPeriods().forEach { period ->
            val sum = sums[period.id]
            val total = sum?.totalMinor ?: 0L
            val reimbursed = sum?.reimbursedMinor ?: 0L
            val status = PendingRules.periodStatus(
                statementSent = period.statementId != null,
                pending = Money(total - reimbursed, CurrencyCode.PEN),
            )
            val updated = period.copy(totalMinor = total, reimbursedMinor = reimbursed, status = status)
            if (updated != period) pools.upsertPeriod(updated)
        }
    }
}
