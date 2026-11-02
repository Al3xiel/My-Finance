package com.al3xiel.my_finances.feature.reports.domain.repository

import com.al3xiel.my_finances.feature.reports.domain.model.LedgerRow
import com.al3xiel.my_finances.feature.reports.domain.model.PartDetail
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportSummary

/** Lecturas del periodo; el cálculo vive en `ReportCalculator`. Las cantidades se miden al cierre del periodo (D35). */
interface ReportRepository {
    suspend fun parts(period: ReportPeriod): List<PartRow>

    suspend fun ledger(period: ReportPeriod): List<LedgerRow>

    /** Detalle por `partId` para la exportación. */
    suspend fun partDetails(period: ReportPeriod): Map<String, PartDetail>

    /** Resumen listo para Inicio (S10) y para S40. */
    suspend fun summary(period: ReportPeriod): ReportSummary
}
