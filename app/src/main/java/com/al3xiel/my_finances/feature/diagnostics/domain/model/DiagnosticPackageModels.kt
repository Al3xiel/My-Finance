package com.al3xiel.my_finances.feature.diagnostics.domain.model

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.AlteredEvent
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.AnonymizedEvent
import java.time.Instant
import kotlinx.serialization.Serializable

/** Un asiento del libro contable de la cuenta tal como lo calculó la app al exportar, con el saldo acumulado. */
@Serializable
data class LedgerLine(
    val sequence: Long,
    val movementId: String?,
    val accountId: String,
    val deltaMinor: Long,
    val entryKind: String,
    val occurredOn: String,
    val isVoided: Boolean,
    val voidedSequence: Long?,
    val balanceMinor: Long,
)

/** Estado de la conciliación al exportar; se compara con lo que recalcula el código actual. */
@Serializable
data class ReconciliationState(
    val reconciliationId: String,
    val accountId: String,
    val currency: CurrencyCode,
    val realMinor: Long,
    val snapshotComputedMinor: Long,
    val checkedSequence: Long,
    val status: String,
    val cause: String?,
    val adjustmentMinor: Long?,
    val needsReview: Boolean,
)

@Serializable
data class OriginBalanceLine(val accountId: String, val sourceId: String, val amountMinor: Long)

/** `projections.json`: lo que la app calculaba al exportar. `originBalances` solo viaja con el alcance `FULL` (dato secundario). */
@Serializable
data class PackageProjections(
    val accountBalances: Map<String, Long>,
    val ledger: List<LedgerLine>,
    val reconciliation: ReconciliationState?,
    val projectorVersions: Map<String, Int>,
    val originBalances: List<OriginBalanceLine>?,
)

/** Todo lo que el empaquetador necesita; no lee la base. `summaryPdf` ya viene dibujado. */
data class DiagnosticPackageInput(
    val exportId: String,
    val scope: DiagnosticScope,
    val reconciliationId: ReconciliationId?,
    val anonymized: Boolean,
    val exportedUpToSequence: Long,
    val exportedAt: Instant,
    val appVersion: String,
    val androidVersion: String,
    val events: List<AnonymizedEvent>,
    val alteredEvents: List<AlteredEvent>,
    val projections: PackageProjections,
    val summaryPdf: ByteArray,
)
