package com.al3xiel.my_finances.feature.pools.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.PoolPeriod
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import com.al3xiel.my_finances.feature.pools.domain.model.StatementLine
import com.al3xiel.my_finances.feature.pools.domain.model.StatementPart
import java.time.LocalDate

/** Arma la rendición: solo las partes con algo pendiente, por fecha y luego por orden de registro. */
object SettlementStatementBuilder {
    fun build(
        pool: Pool,
        responsibleAlias: String,
        period: PoolPeriod,
        parts: List<StatementPart>,
        previousPending: Money,
        generatedOn: LocalDate,
    ): SettlementStatementContent {
        val lines = parts
            .filter { it.pending.isPositive }
            .sortedWith(compareBy<StatementPart> { it.occurredOn }.thenBy { it.createdSequence })
            .map { StatementLine(it.partId, it.occurredOn, it.categoryName, it.tagNames, it.amount, it.pending) }
        return SettlementStatementContent(
            poolId = pool.id,
            periodId = period.id,
            poolName = pool.name,
            responsibleAlias = responsibleAlias,
            periodFrom = period.openedOn,
            generatedOn = generatedOn,
            lines = lines,
            pendingTotal = Money(lines.sumOf { it.pending.amountMinor }, CurrencyCode.PEN),
            previousPending = previousPending,
        )
    }
}
