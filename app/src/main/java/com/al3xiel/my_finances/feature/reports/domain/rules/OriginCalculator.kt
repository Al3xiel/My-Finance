package com.al3xiel.my_finances.feature.reports.domain.rules

import com.al3xiel.my_finances.core.money.LargestRemainder
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.feature.reports.domain.model.AccountOrigin
import com.al3xiel.my_finances.feature.reports.domain.model.OriginAttributionRecord
import com.al3xiel.my_finances.feature.reports.domain.model.OriginBalanceRow
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSlice
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSourceIds
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyLink
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNode
import com.al3xiel.my_finances.feature.reports.domain.model.SankeyNodeKind
import com.al3xiel.my_finances.feature.reports.domain.model.SourceSpendRow

/** Cálculos de S44 sobre `origin_balances` y `origin_attributions`; todo es estimado (docs/04 §5.1). */
object OriginCalculator {
    private const val WHOLE = 10_000L

    fun composition(rows: List<OriginBalanceRow>): List<AccountOrigin> =
        rows.groupBy { it.accountId }.toSortedMap().map { (accountId, own) ->
            val shares = LargestRemainder.distribute(WHOLE, own.filter { it.amountMinor > 0 }.map { it.sourceId to it.amountMinor })
            val slices = own.map { OriginSlice(it.sourceId, it.amountMinor, (shares[it.sourceId] ?: 0L).toInt()) }
                .sortedWith(compareByDescending<OriginSlice> { it.amountMinor }.thenBy { it.sourceId })
            AccountOrigin(
                accountId, own.sumOf { it.amountMinor }, slices,
                hasShortfall = own.any { it.sourceId == OriginSourceIds.UNKNOWN_ADJUSTMENT && it.amountMinor < 0 },
            )
        }

    fun spendOf(sourceId: String, records: List<OriginAttributionRecord>): List<SourceSpendRow> =
        records.filter { it.sourceId == sourceId && it.amountMinor > 0 }
            .groupBy { Triple(it.accountId, it.categoryId, it.kind) }
            .map { (key, group) -> SourceSpendRow(key.first, key.second, key.third, group.sumOf { it.amountMinor }) }
            .sortedWith(
                compareByDescending<SourceSpendRow> { it.amountMinor }.thenBy { it.accountId }.thenBy { it.categoryId.orEmpty() },
            )

    /**
     * Fuente → cuenta de donde salió → categoría de primer nivel, "Préstamos" o "No identificado" (D42). Lo que entra a cada
     * cuenta es lo que sale de ella, porque ambos lados suman las mismas atribuciones.
     */
    fun sankey(records: List<OriginAttributionRecord>): List<SankeyLink> {
        val positive = records.filter { it.amountMinor > 0 }
        val sourceToAccount = positive.groupBy { it.sourceId to it.accountId }
            .map { (key, group) ->
                SankeyLink(SankeyNode(SankeyNodeKind.SOURCE, key.first), SankeyNode(SankeyNodeKind.ACCOUNT, key.second), group.sumOf { it.amountMinor })
            }
        val accountToTarget = positive.groupBy { it.accountId to target(it) }
            .map { (key, group) -> SankeyLink(SankeyNode(SankeyNodeKind.ACCOUNT, key.first), key.second, group.sumOf { it.amountMinor }) }
        return (sourceToAccount.sortedWith(order()) + accountToTarget.sortedWith(order()))
    }

    private fun target(record: OriginAttributionRecord): SankeyNode = when {
        record.kind == LedgerEntryKind.LOAN_OUT -> SankeyNode(SankeyNodeKind.LOANS, "")
        record.categoryId == null -> SankeyNode(SankeyNodeKind.UNIDENTIFIED, "")
        else -> SankeyNode(SankeyNodeKind.CATEGORY, record.categoryParentId ?: record.categoryId)
    }

    private fun order() = compareByDescending<SankeyLink> { it.amountMinor }
        .thenBy { it.from.id }.thenBy { it.to.kind }.thenBy { it.to.id }
}
