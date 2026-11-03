package com.al3xiel.my_finances.feature.reports.domain.repository

import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData

/**
 * Escribe el reporte en el destino que eligió el usuario con el selector del sistema (SAF, docs/02 RX2), directo y sin
 * archivo temporal. Devuelven `false` si no se pudo abrir o escribir.
 */
interface ReportFileRepository {
    suspend fun writeCsv(uri: String, rows: List<CsvReportRow>, includeNotes: Boolean): Boolean

    suspend fun writePdf(uri: String, data: PdfReportData): Boolean
}
