package com.al3xiel.my_finances.feature.diagnostics.domain.model

import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import java.time.Instant
import java.time.LocalDate

/** Contenido de `summary.pdf`: nada que identifique a nadie (ni alias ni notas), aunque el paquete no esté anonimizado. */
data class DiagnosticSummary(
    val scope: DiagnosticScope,
    val anonymized: Boolean,
    val eventCount: Int,
    val firstOccurredOn: LocalDate?,
    val lastOccurredOn: LocalDate?,
    val exportedAt: Instant,
    val reconciliation: ReconciliationState?,
    val accountCount: Int,
)
