package com.al3xiel.my_finances.feature.diagnostics.domain.model

data class BalanceComparison(val accountId: String, val exportedMinor: Long?, val recalculatedMinor: Long?) {
    val diverges: Boolean get() = exportedMinor != recalculatedMinor
}

/** Un asiento (o ninguno) de cada lado para un `sequence` donde difieren. */
data class LedgerComparison(val sequence: Long, val accountId: String, val exported: ExportedLedgerLine?, val recalculated: ExportedLedgerLine?)

data class VersionDifference(val projector: String, val exported: Int?, val current: Int?)

/** El estado de la conciliación exportado contra el recalculado; `needsReview` no se compara: solo un replay por versión lo marca. */
data class ReconciliationComparison(
    val reconciliationId: String,
    val realMinor: Long,
    val exportedComputedMinor: Long,
    val recalculatedComputedMinor: Long?,
    val differingFields: List<String>,
) {
    val diverges: Boolean get() = differingFields.isNotEmpty()
}

data class OriginDifference(val accountId: String, val sourceId: String, val exportedMinor: Long?, val recalculatedMinor: Long?)

/**
 * "Reporte de divergencias" (P16): lo que el paquete decía contra lo que el código actual recalcula. `origin_balances` es un
 * dato secundario: se muestra aparte y no cambia el veredicto.
 */
data class DivergenceReport(
    val exportId: String,
    val balances: List<BalanceComparison>,
    val firstDivergentSequence: Long?,
    val divergentLedger: List<LedgerComparison>,
    val versionDifferences: List<VersionDifference>,
    val reconciliation: ReconciliationComparison?,
    val originDifferences: List<OriginDifference>?,
) {
    val diverges: Boolean
        get() = balances.any { it.diverges } || divergentLedger.isNotEmpty() || reconciliation?.diverges == true
}
