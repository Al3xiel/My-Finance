package com.al3xiel.my_finances.feature.reports.domain.model

import com.al3xiel.my_finances.domain.model.LedgerEntryKind

/** Cuánto del saldo de una cuenta viene de una fuente (fila de `origin_balances`). */
data class OriginBalanceRow(val accountId: String, val sourceId: String, val amountMinor: Long)

/** `basisPoints` (10 000 = 100%) por mayor residuo; una fuente con monto negativo (faltante) no participa y queda en 0. */
data class OriginSlice(val sourceId: String, val amountMinor: Long, val basisPoints: Int)

/** Composición de origen de una cuenta (`OriginComposition`); `hasShortfall` si el ajuste no identificado quedó negativo. */
data class AccountOrigin(val accountId: String, val balanceMinor: Long, val slices: List<OriginSlice>, val hasShortfall: Boolean)

/** Una porción de un gasto atribuida a una fuente, con la cuenta de donde salió y su categoría (`null` en préstamos y ajustes). */
data class OriginAttributionRecord(
    val sourceId: String,
    val accountId: String,
    val kind: LedgerEntryKind,
    val categoryId: String?,
    val categoryParentId: String?,
    val amountMinor: Long,
)

/** "¿En qué se gastó lo de X?": lo de una fuente en una cuenta y categoría. */
data class SourceSpendRow(val accountId: String, val categoryId: String?, val kind: LedgerEntryKind, val amountMinor: Long)

enum class SankeyNodeKind { SOURCE, ACCOUNT, CATEGORY, LOANS, UNIDENTIFIED }

/** `id` vacío en los nodos "Préstamos" y "No identificado". */
data class SankeyNode(val kind: SankeyNodeKind, val id: String)

data class SankeyLink(val from: SankeyNode, val to: SankeyNode, val amountMinor: Long)
