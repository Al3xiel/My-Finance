package com.al3xiel.my_finances.feature.reports.domain.model

import java.time.LocalDate

/** Una fila por parte de gasto (D38). `ownAmountMinor` es lo que cuenta como gasto propio de esa parte. */
data class CsvReportRow(
    val date: LocalDate,
    val account: String,
    val category: String,
    val tags: List<String>,
    val ownAmountMinor: Long,
    val type: String,
    val note: String?,
)

/** Una tabla del reporte: encabezado, columnas y filas ya formateadas (nombres y montos listos para imprimir). */
data class PdfSection(val heading: String, val columns: List<String>, val rows: List<List<String>>)

data class PdfReportData(val title: String, val subtitle: String, val notice: String, val sections: List<PdfSection>)
