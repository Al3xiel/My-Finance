package com.al3xiel.my_finances.feature.diagnostics.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSummary
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticSummaryRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Dibuja `summary.pdf` con `PdfDocument`: una página con conteos, fechas y el estado de la conciliación; nunca nombres ni notas. */
class PdfDiagnosticSummaryRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) : DiagnosticSummaryRenderer {
    private val body = Paint().apply { textSize = 12f; isAntiAlias = true }
    private val title = Paint(body).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }

    override fun render(summary: DiagnosticSummary): ByteArray {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        var y = MARGIN + 14f
        page.canvas.drawText(context.getString(R.string.diagnostic_summary_title), MARGIN, y, title)
        y += LINE * 2
        for (line in lines(summary)) {
            page.canvas.drawText(line, MARGIN, y, body)
            y += LINE
        }
        document.finishPage(page)
        val out = ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    private fun lines(summary: DiagnosticSummary): List<String> = buildList {
        add(context.getString(R.string.diagnostic_summary_scope, summary.scope.name))
        add(context.getString(if (summary.anonymized) R.string.diagnostic_summary_anonymized else R.string.diagnostic_summary_not_anonymized))
        add(context.getString(R.string.diagnostic_summary_events, summary.eventCount))
        if (summary.firstOccurredOn != null && summary.lastOccurredOn != null) {
            add(context.getString(R.string.diagnostic_summary_range, summary.firstOccurredOn.toString(), summary.lastOccurredOn.toString()))
        }
        add(context.getString(R.string.diagnostic_summary_exported_at, summary.exportedAt.toString()))
        add(context.getString(R.string.diagnostic_summary_accounts, summary.accountCount))
        summary.reconciliation?.let {
            add(context.getString(R.string.diagnostic_summary_reconciliation, it.status, it.realMinor, it.snapshotComputedMinor, it.currency.name))
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40f
        const val LINE = 18f
    }
}
