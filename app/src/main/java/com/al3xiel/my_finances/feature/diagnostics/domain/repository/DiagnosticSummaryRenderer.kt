package com.al3xiel.my_finances.feature.diagnostics.domain.repository

import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSummary

/** Dibuja `summary.pdf`. Sin nombres ni notas: el resumen solo lleva conteos, fechas y estado. */
fun interface DiagnosticSummaryRenderer {
    fun render(summary: DiagnosticSummary): ByteArray
}
