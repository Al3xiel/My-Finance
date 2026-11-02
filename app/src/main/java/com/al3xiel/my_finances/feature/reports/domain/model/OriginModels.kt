package com.al3xiel.my_finances.feature.reports.domain.model

import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.SystemCatalog

/** Ids de las fuentes del sistema que usa el motor (D10, D39). */
object OriginSourceIds {
    val OPENING_BALANCE: String = SystemCatalog.incomeSources[0].id.value
    val UNKNOWN_ADJUSTMENT: String = SystemCatalog.incomeSources[1].id.value
}

/** Parte de un gasto, en su orden de registro. */
data class OriginPart(val partId: String, val amountMinor: Long)

/**
 * Un asiento vigente del libro, con lo que el motor necesita. `orderKey` es el menor `sequence` de su movimiento
 * (incluidos los anulados) o el propio (D40); `sourceId` es la fuente del movimiento (ingresos, reembolsos, cobros).
 */
data class OriginEntry(
    val id: String,
    val sequence: Long,
    val orderKey: Long,
    val accountId: String,
    val deltaMinor: Long,
    val kind: LedgerEntryKind,
    val movementId: String?,
    val sourceId: String?,
    val parts: List<OriginPart>,
)

/** `partId` vacío cuando el asiento no tiene parte (préstamo, ajuste −) (D41). */
data class OriginAttributionRow(val entryId: String, val partId: String, val sourceId: String, val amountMinor: Long)
