package com.al3xiel.my_finances.feature.reports.data

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData
import java.io.OutputStream

/**
 * Dibuja el reporte del periodo con `PdfDocument` (D38): título, aviso de que el archivo no está cifrado y una tabla por
 * sección. La primera columna va a la izquierda y las demás alineadas a la derecha; sigue en otra página al llenarse.
 */
class PdfReportRenderer {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val lineHeight = 18f
    private val bottom = pageHeight - 50f

    private val body = Paint().apply { textSize = 11f; isAntiAlias = true }
    private val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
    private val heading = Paint(bold).apply { textSize = 14f }
    private val title = Paint(bold).apply { textSize = 18f }

    fun render(data: PdfReportData, out: OutputStream) {
        val document = PdfDocument()
        var number = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number).create())
        var y = margin + 14f

        fun newPageIfNeeded(height: Float) {
            if (y + height <= bottom) return
            document.finishPage(page)
            number += 1
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, number).create())
            y = margin + 14f
        }

        page.canvas.drawText(data.title, margin, y, title)
        y += lineHeight + 4f
        page.canvas.drawText(data.subtitle, margin, y, body)
        y += lineHeight
        page.canvas.drawText(data.notice, margin, y, bold)
        y += lineHeight * 2

        for (section in data.sections) {
            newPageIfNeeded(lineHeight * 3)
            page.canvas.drawText(section.heading, margin, y, heading)
            y += lineHeight
            drawRow(page, y, section.columns, bold)
            y += lineHeight
            for (row in section.rows) {
                newPageIfNeeded(lineHeight)
                drawRow(page, y, row, body)
                y += lineHeight
            }
            y += lineHeight
        }
        document.finishPage(page)
        document.writeTo(out)
        document.close()
        out.flush()
    }

    private fun drawRow(page: PdfDocument.Page, y: Float, cells: List<String>, paint: Paint) {
        val right = pageWidth - margin
        val columnWidth = 90f
        val numeric = cells.size - 1
        val labelWidth = right - margin - columnWidth * numeric - 8f
        cells.forEachIndexed { i, cell ->
            if (i == 0) {
                val fit = paint.breakText(cell, true, labelWidth, null)
                page.canvas.drawText(cell, 0, fit, margin, y, paint)
            } else {
                val edge = right - columnWidth * (numeric - i)
                page.canvas.drawText(cell, edge - paint.measureText(cell), y, paint)
            }
        }
    }
}
