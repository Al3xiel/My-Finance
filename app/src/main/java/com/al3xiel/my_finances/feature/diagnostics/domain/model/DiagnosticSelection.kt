package com.al3xiel.my_finances.feature.diagnostics.domain.model

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import java.time.LocalDate

/**
 * Qué se quiere exportar. `RECONCILIATION` pide la conciliación; `ACCOUNT_HISTORY` pide la cuenta o, si no se da, la de la
 * conciliación; `FULL` no pide nada (también se usa desde las herramientas de desarrollador, sin conciliación).
 */
data class DiagnosticSelectionRequest(
    val scope: DiagnosticScope,
    val reconciliationId: ReconciliationId? = null,
    val accountId: AccountId? = null,
)

/** Los eventos elegidos y la vista previa que ve quien exporta: cuántos, de qué fechas y qué campos se anonimizarían. */
data class DiagnosticSelection(
    val scope: DiagnosticScope,
    val accountId: AccountId?,
    val reconciliationId: ReconciliationId?,
    val events: List<StoredEvent>,
    /** Último `sequence` del registro al elegir: con él se resuelven los alias del diccionario (04 §3.3.2). */
    val exportedUpToSequence: Long,
    val firstOccurredOn: LocalDate?,
    val lastOccurredOn: LocalDate?,
    val anonymizedFields: List<String>,
) {
    val eventCount: Int get() = events.size
}
