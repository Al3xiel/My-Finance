package com.al3xiel.my_finances.feature.diagnostics.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSummary
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDiagnosticSummaryRendererTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun summary(anonymized: Boolean) = DiagnosticSummary(
        scope = DiagnosticScope.RECONCILIATION,
        anonymized = anonymized,
        eventCount = 42,
        firstOccurredOn = LocalDate.of(2026, 10, 1),
        lastOccurredOn = LocalDate.of(2026, 11, 2),
        exportedAt = Instant.parse("2026-11-02T12:00:00Z"),
        reconciliation = null,
        accountCount = 2,
    )

    @Test
    fun drawsARealPdfDocument() {
        val bytes = PdfDiagnosticSummaryRenderer(context).render(summary(anonymized = true))
        assertEquals("%PDF", String(bytes.copyOf(4), Charsets.ISO_8859_1))
        assertTrue(bytes.size > 200)
    }

    @Test
    fun worksWithoutDatesAndWithoutAnonymizing() {
        val bytes = PdfDiagnosticSummaryRenderer(context).render(summary(anonymized = false).copy(firstOccurredOn = null, lastOccurredOn = null))
        assertEquals("%PDF", String(bytes.copyOf(4), Charsets.ISO_8859_1))
    }
}
