package com.al3xiel.my_finances.feature.reports.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada columna numérica del PDF mide lo que necesita su texto: un encabezado largo no puede invadir a su vecina. */
class PdfColumnWidthsTest {
    // Medida de mentira: 6 puntos por letra, y la negrita (encabezados) un poco más ancha.
    private val measure: (String, Boolean) -> Float = { text, bold -> text.length * if (bold) 6.5f else 6f }

    private val columns = listOf("Nombre", "Entradas", "Salidas", "Transferencias netas")
    private val rows = listOf(
        listOf("BBVA", "S/ 700.00", "S/ 540.00", "S/ 0.00"),
        listOf("Efectivo", "S/ 0.00", "S/ 0.00", "S/ 0.00"),
    )

    @Test fun `a long header gets room for itself plus the gap`() {
        val widths = numericColumnWidths(columns, rows, measure, minWidth = 60f, gap = 12f)
        assertEquals(3, widths.size)
        assertEquals("Transferencias netas".length * 6.5f + 12f, widths[2], 0.001f)
    }

    @Test fun `short content keeps the minimum width`() {
        val widths = numericColumnWidths(columns, rows, measure, minWidth = 90f, gap = 12f)
        assertEquals(90f, widths[0], 0.001f)
        assertEquals(90f, widths[1], 0.001f)
    }

    @Test fun `a wide amount in a row widens its column too`() {
        val wide = rows + listOf(listOf("Ahorros", "S/ 1,234,567.89", "S/ 0.00", "S/ 0.00"))
        val widths = numericColumnWidths(columns, wide, measure, minWidth = 60f, gap = 12f)
        assertEquals("S/ 1,234,567.89".length * 6f + 12f, widths[0], 0.001f)
    }

    @Test fun `every column fits its header so neighbors never overlap`() {
        val widths = numericColumnWidths(columns, rows, measure, minWidth = 60f, gap = 12f)
        widths.forEachIndexed { i, width -> assertTrue(width >= measure(columns[i + 1], true)) }
    }

    @Test fun `a section with only a label column has no numeric columns`() {
        assertEquals(emptyList<Float>(), numericColumnWidths(listOf("Nombre"), emptyList(), measure, minWidth = 60f, gap = 12f))
    }
}
