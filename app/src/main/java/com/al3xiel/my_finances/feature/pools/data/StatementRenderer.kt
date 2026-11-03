package com.al3xiel.my_finances.feature.pools.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.share.SharedFilesCleaner
import com.al3xiel.my_finances.domain.model.StatementId
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.feature.pools.domain.model.SettlementStatementContent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

data class RenderedStatement(val uri: Uri, val mimeType: String)

/**
 * Dibuja la rendición en imagen (PNG) o PDF dentro de `cacheDir/share` y la expone con el `FileProvider` no exportado.
 * El nombre del archivo no lleva nombres ni montos. Quien comparte borra el archivo con [SharedFilesCleaner].
 */
class StatementRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun render(statementId: StatementId, content: SettlementStatementContent, format: StatementFormat): RenderedStatement {
        val rows = StatementLayout.rows(content, labels())
        val dir = File(context.cacheDir, SharedFilesCleaner.SHARE_DIR).apply { mkdirs() }
        val safeId = statementId.value.filter { it.isLetterOrDigit() || it == '-' }
        val (file, mime) = when (format) {
            StatementFormat.IMAGE -> File(dir, "rendicion-$safeId.png").also { writePng(it, rows) } to "image/png"
            StatementFormat.PDF -> File(dir, "rendicion-$safeId.pdf").also { writePdf(it, rows) } to "application/pdf"
        }
        return RenderedStatement(FileProvider.getUriForFile(context, "${context.packageName}.share", file), mime)
    }

    /** Selector de apps con permiso de lectura temporal sobre ese único archivo. */
    fun shareIntent(rendered: RenderedStatement, title: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = rendered.mimeType
            putExtra(Intent.EXTRA_STREAM, rendered.uri)
            clipData = ClipData.newRawUri("", rendered.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun labels() = StatementLabels(
        responsible = context.getString(R.string.statement_responsible),
        period = context.getString(R.string.statement_period),
        header = context.getString(R.string.statement_header),
        pendingHeader = context.getString(R.string.statement_pending_header),
        periodPending = context.getString(R.string.statement_period_pending),
        previousPending = context.getString(R.string.statement_previous_pending),
        grandTotal = context.getString(R.string.statement_grand_total),
    )

    private fun writePng(file: File, rows: List<StatementRow>) {
        val height = (MARGIN * 2 + rows.sumOf { heightOf(it.style).toDouble() }).toInt()
        val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
            var y = MARGIN
            rows.forEach { y = draw(canvas, it, y, IMAGE_WIDTH.toFloat(), 1f) }
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writePdf(file: File, rows: List<StatementRow>) {
        val scale = PDF_WIDTH / IMAGE_WIDTH.toFloat()
        val document = PdfDocument()
        try {
            var number = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, number).create())
            var y = MARGIN * scale
            rows.forEach { row ->
                val rowHeight = heightOf(row.style) * scale
                if (y + rowHeight > PDF_HEIGHT - MARGIN * scale) {
                    document.finishPage(page)
                    number++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PDF_WIDTH, PDF_HEIGHT, number).create())
                    y = MARGIN * scale
                }
                y = draw(page.canvas, row, y, PDF_WIDTH.toFloat(), scale)
            }
            document.finishPage(page)
            FileOutputStream(file).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    /** Dibuja la fila con su borde superior en `top` y devuelve el `top` de la siguiente. */
    private fun draw(canvas: Canvas, row: StatementRow, top: Float, width: Float, scale: Float): Float {
        val rowHeight = heightOf(row.style) * scale
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = textSizeOf(row.style) * scale
            typeface = if (row.style in bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val left = MARGIN * scale
        val right = width - MARGIN * scale
        val baseline = top + rowHeight * 0.68f
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(row.right, right, baseline, paint)
        val room = right - left - paint.measureText(row.right) - 16 * scale
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(fit(paint, row.left, room), left, baseline, paint)
        if (row.style == RowStyle.HEADER) {
            canvas.drawLine(left, top + rowHeight - 4 * scale, right, top + rowHeight - 4 * scale, paint)
        }
        return top + rowHeight
    }

    private fun fit(paint: Paint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val count = paint.breakText(text, true, (maxWidth - paint.measureText("…")).coerceAtLeast(0f), null)
        return text.substring(0, count) + "…"
    }

    private fun heightOf(style: RowStyle) = when (style) {
        RowStyle.TITLE -> 72f
        RowStyle.SUBTITLE -> 44f
        RowStyle.HEADER -> 56f
        RowStyle.LINE -> 48f
        RowStyle.TOTAL -> 56f
        RowStyle.EMPHASIS -> 72f
    }

    private fun textSizeOf(style: RowStyle) = when (style) {
        RowStyle.TITLE -> 44f
        RowStyle.SUBTITLE -> 28f
        RowStyle.HEADER -> 28f
        RowStyle.LINE -> 30f
        RowStyle.TOTAL -> 32f
        RowStyle.EMPHASIS -> 38f
    }

    private companion object {
        const val IMAGE_WIDTH = 1080
        const val PDF_WIDTH = 595
        const val PDF_HEIGHT = 842
        const val MARGIN = 48f
        val bold = setOf(RowStyle.TITLE, RowStyle.HEADER, RowStyle.EMPHASIS)
    }
}
