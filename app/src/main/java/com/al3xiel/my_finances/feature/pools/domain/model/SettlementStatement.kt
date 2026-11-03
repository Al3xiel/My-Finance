package com.al3xiel.my_finances.feature.pools.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.PoolPeriodId
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolPeriodStatus
import java.time.LocalDate

data class PoolPeriod(
    val id: PoolPeriodId,
    val poolId: PoolId,
    val openedOn: LocalDate,
    val closedOn: LocalDate?,
    val status: PoolPeriodStatus,
    val statementId: StatementId?,
    val total: Money,
    val reimbursed: Money,
) {
    val pending: Money get() = total - reimbursed
}

/** Parte de un gasto del periodo, con los nombres que se muestran en la rendición. */
data class StatementPart(
    val partId: PartId,
    val occurredOn: LocalDate,
    val createdSequence: Long,
    val categoryName: String,
    val tagNames: List<String>,
    val amount: Money,
    val pending: Money,
)

data class StatementLine(
    val partId: PartId,
    val occurredOn: LocalDate,
    val categoryName: String,
    val tagNames: List<String>,
    val amount: Money,
    val pending: Money,
)

/**
 * Lo que se muestra y se comparte: sin notas ni saldos de cuentas (docs/02 S7). `previousPending` es lo que quedó de
 * rendiciones anteriores y va aparte, no dentro de `pendingTotal`.
 */
data class SettlementStatementContent(
    val poolId: PoolId,
    val periodId: PoolPeriodId,
    val poolName: String,
    val responsibleAlias: String,
    val periodFrom: LocalDate,
    val generatedOn: LocalDate,
    val lines: List<StatementLine>,
    val pendingTotal: Money,
    val previousPending: Money,
) {
    val grandTotal: Money get() = pendingTotal + previousPending
}

data class GeneratedStatement(
    val statementId: StatementId,
    val content: SettlementStatementContent,
)
