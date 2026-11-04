package com.al3xiel.my_finances.feature.reports.data

import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import java.io.OutputStream

/**
 * CSV del periodo (docs/02 §6, RX3): UTF-8 con BOM, coma, importes con punto decimal, notas solo si se piden; las celdas que
 * un programa de hojas de cálculo leería como fórmula se prefijan con `'`.
 */
object CsvReportWriter {
    private const val EOL = "\r\n"
    /** Encabezados fijos en español (D38): los lee una hoja de cálculo, no la interfaz. */
    private val HEADER = listOf("Fecha", "Cuenta", "Categoría", "Conceptos", "Monto propio", "Tipo")
    private val formulaStarts = charArrayOf('=', '+', '-', '@', '\t', '\r')

    fun write(out: OutputStream, rows: List<CsvReportRow>, includeNotes: Boolean) {
        val text = StringBuilder()
        text.append('\uFEFF')
        val header = HEADER.toMutableList()
        if (includeNotes) header += "Nota"
        text.append(header.joinToString(",") { cell(it) }).append(EOL)
        for (r in rows) {
            val cells = mutableListOf(r.date.toString(), r.account, r.category, r.tags.joinToString("; "), amountText(r.ownAmountMinor), r.type)
            if (includeNotes) cells += r.note.orEmpty()
            text.append(cells.joinToString(",") { cell(it) }).append(EOL)
        }
        out.write(text.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /** Dos decimales con aritmética entera, sin separador de miles. */
    fun amountText(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        val abs = Math.abs(minor)
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    fun cell(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in formulaStarts) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
