package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.reports.domain.model.AccountFlow
import com.al3xiel.my_finances.feature.reports.domain.model.CategoryShare
import com.al3xiel.my_finances.feature.reports.domain.model.LedgerRow
import com.al3xiel.my_finances.feature.reports.domain.model.OthersSpend
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary
import com.al3xiel.my_finances.feature.reports.domain.model.TagTotal

/** Cálculo puro de los reportes (D35): mismas filas, mismos números. */
object ReportCalculator {
    fun summary(parts: List<PartRow>, ledger: List<LedgerRow>): ReportSummary {
        val pooled = parts.filter { it.hasPool }
        return ReportSummary(
            income = ledger.filter { it.kind == LedgerEntryKind.INCOME }.sumOf { it.delta },
            ownSpend = parts.sumOf { it.ownAmount },
            others = OthersSpend(pooled.sumOf { it.amount }, pooled.sumOf { it.reimbursed }),
            unidentified = ledger.filter { it.kind == LedgerEntryKind.ADJUSTMENT }.sumOf { it.delta },
        )
    }

    /** Agrupa por categoría raíz (las subcategorías suman a su padre); suma 100% sobre el gasto propio. */
    fun byCategory(parts: List<PartRow>): List<CategoryShare> {
        val own = parts.filter { it.ownAmount > 0 }
        val roots = own.groupBy { it.categoryParentId ?: it.categoryId }
        val rootBp = Percentages.basisPoints(roots.map { (id, rows) -> id to rows.sumOf { it.ownAmount } })
        return roots.map { (rootId, rows) ->
            val kids = rows.filter { it.categoryId != rootId }.groupBy { it.categoryId }
                .map { (id, r) -> id to r.sumOf { it.ownAmount } }
            val kidBp = Percentages.basisPoints(kids)
            CategoryShare(
                categoryId = rootId,
                amount = rows.sumOf { it.ownAmount },
                basisPoints = rootBp.getValue(rootId),
                children = kids.sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
                    .map { CategoryShare(it.first, it.second, kidBp.getValue(it.first)) },
            )
        }.sortedWith(compareByDescending<CategoryShare> { it.amount }.thenBy { it.categoryId })
    }

    /** Cada concepto con el monto propio de sus partes; no suman entre sí. */
    fun byTag(parts: List<PartRow>): List<TagTotal> =
        parts.filter { it.ownAmount > 0 }.flatMap { p -> p.tagIds.map { it to p } }
            .groupBy({ it.first }, { it.second })
            .map { (tag, rows) ->
                TagTotal(
                    tagId = tag,
                    amount = rows.sumOf { it.ownAmount },
                    byCategory = rows.groupBy { it.categoryId }.map { (c, r) -> c to r.sumOf { it.ownAmount } }
                        .sortedBy { it.first },
                )
            }.sortedWith(compareByDescending<TagTotal> { it.amount }.thenBy { it.tagId })

    /** Entradas, salidas y transferencias netas (aparte) por cuenta (S43); el asiento de apertura no cuenta. */
    fun byAccount(ledger: List<LedgerRow>): List<AccountFlow> =
        ledger.groupBy { it.accountId }.map { (id, rows) ->
            var inflow = 0L
            var outflow = 0L
            var transfers = 0L
            for (r in rows) when (r.kind) {
                LedgerEntryKind.INCOME, LedgerEntryKind.REIMBURSEMENT_IN, LedgerEntryKind.LOAN_IN -> inflow += r.delta
                LedgerEntryKind.EXPENSE, LedgerEntryKind.FEE, LedgerEntryKind.LOAN_OUT -> outflow -= r.delta
                LedgerEntryKind.ADJUSTMENT -> if (r.delta >= 0) inflow += r.delta else outflow -= r.delta
                LedgerEntryKind.TRANSFER_IN, LedgerEntryKind.TRANSFER_OUT -> transfers += r.delta
                LedgerEntryKind.OPENING -> Unit
            }
            AccountFlow(id, inflow, outflow, transfers)
        }.sortedBy { it.accountId }
}
