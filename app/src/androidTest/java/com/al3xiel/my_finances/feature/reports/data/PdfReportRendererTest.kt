package com.al3xiel.my_finances.feature.reports.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData
import com.al3xiel.my_finances.feature.reports.domain.model.PdfSection
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfReportRendererTest {
    private fun render(rows: Int): ByteArray {
        val section = PdfSection("Por categoría", listOf("Categoría", "Monto", "%"), List(rows) { listOf("Categoría $it", "S/ 10.00", "1,00 %") })
        val out = ByteArrayOutputStream()
        PdfReportRenderer().render(PdfReportData("Reporte", "octubre 2026", "Este archivo no está cifrado", listOf(section)), out)
        return out.toByteArray()
    }

    private fun pages(pdf: ByteArray) = Regex("/Type\\s*/Page\\b").findAll(String(pdf, Charsets.ISO_8859_1)).count()

    @Test fun writesAValidPdfWithOnePageForASmallReport() {
        val pdf = render(rows = 5)
        assertEquals("%PDF", String(pdf, 0, 4, Charsets.ISO_8859_1))
        assertEquals(1, pages(pdf))
    }

    @Test fun aLongTableContinuesOnAnotherPage() {
        assertTrue(pages(render(rows = 120)) >= 2)
    }

    @Test fun anEmptyReportStillProducesADocument() {
        assertTrue(render(rows = 0).size > 100)
    }
}
