package com.al3xiel.my_finances.feature.reports.domain.model

import com.al3xiel.my_finances.domain.model.LedgerEntryKind

/**
 * Una parte de un movimiento activo del periodo. `reimbursed` es lo reembolsado hasta el cierre del periodo (D35);
 * `hasPool` marca las partes de un cúmulo (gasto por otros).
 */
data class PartRow(
    val partId: String,
    val accountId: String,
    val categoryId: String,
    val categoryParentId: String?,
    val amount: Long,
    val hasPool: Boolean,
    val reimbursed: Long,
    val tagIds: List<String>,
) {
    /** Lo que cuenta como gasto propio: toda la parte, o su porción pendiente si es de un cúmulo. */
    val ownAmount: Long get() = if (hasPool) amount - reimbursed else amount
}

/** Lo que el CSV necesita además de la parte: cuándo, de qué clase y la nota del movimiento. */
data class PartDetail(val date: java.time.LocalDate, val kind: com.al3xiel.my_finances.domain.model.PartKind, val note: String?)

/** Asiento vigente del periodo, con el signo del libro. */
data class LedgerRow(val accountId: String, val kind: LedgerEntryKind, val delta: Long)

data class OthersSpend(val total: Long, val reimbursed: Long) {
    val pending: Long get() = total - reimbursed
}

data class ReportSummary(val income: Long, val ownSpend: Long, val others: OthersSpend, val unidentified: Long)

data class CategoryShare(
    val categoryId: String,
    val amount: Long,
    val basisPoints: Int,
    val children: List<CategoryShare> = emptyList(),
)

data class TagTotal(val tagId: String, val amount: Long, val byCategory: List<Pair<String, Long>>)

data class AccountFlow(val accountId: String, val inflow: Long, val outflow: Long, val transfersNet: Long)
