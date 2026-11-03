package com.al3xiel.my_finances.feature.reports.data

import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvReportWriterTest {
    private fun row(
        category: String = "Comida",
        tags: List<String> = emptyList(),
        amount: Long = 1_250,
        note: String? = null,
        account: String = "BBVA",
    ) = CsvReportRow(LocalDate.of(2026, 10, 5), account, category, tags, amount, "EXPENSE", note)

    private fun csv(rows: List<CsvReportRow>, includeNotes: Boolean = false): String {
        val out = ByteArrayOutputStream()
        CsvReportWriter.write(out, rows, includeNotes)
        return out.toByteArray().toString(Charsets.UTF_8)
    }

    @Test fun `starts with a utf-8 bom and the header`() {
        val bytes = ByteArrayOutputStream().also { CsvReportWriter.write(it, emptyList(), false) }.toByteArray()
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
        assertEquals("Fecha,Cuenta,Categoría,Conceptos,Monto propio,Tipo\r\n", csv(emptyList()).removePrefix("﻿"))
    }

    @Test fun `amounts use a decimal point and tags are joined`() {
        val lines = csv(listOf(row(tags = listOf("Viaje", "Trabajo"), amount = 1_205))).removePrefix("﻿").lines()
        assertEquals("2026-10-05,BBVA,Comida,Viaje; Trabajo,12.05,EXPENSE", lines[1])
        assertEquals("0.05", CsvReportWriter.amountText(5))
        assertEquals("100.00", CsvReportWriter.amountText(10_000))
    }

    @Test fun `cells that could run as formulas get a quote prefix`() {
        for (danger in listOf("=SUM(A1)", "+1", "-2", "@cmd", "\tx", "\rx")) {
            val cell = CsvReportWriter.cell(danger)
            assertTrue("$danger -> $cell", cell.removePrefix("\"").startsWith("'"))
        }
        assertEquals("Comida", CsvReportWriter.cell("Comida"))
    }

    @Test fun `quotes commas and line breaks are escaped`() {
        assertEquals("\"a,b\"", CsvReportWriter.cell("a,b"))
        assertEquals("\"di \"\"hola\"\"\"", CsvReportWriter.cell("di \"hola\""))
        assertEquals("\"l1\nl2\"", CsvReportWriter.cell("l1\nl2"))
    }

    @Test fun `notes are written only when asked`() {
        val rows = listOf(row(note = "=cmd"))
        assertTrue(!csv(rows).contains("=cmd"))
        val withNotes = csv(rows, includeNotes = true).removePrefix("﻿").lines()
        assertTrue(withNotes[0].endsWith(",Nota"))
        assertTrue(withNotes[1].endsWith(",'=cmd"))
    }
}
