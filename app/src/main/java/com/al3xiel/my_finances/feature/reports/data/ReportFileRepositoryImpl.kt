package com.al3xiel.my_finances.feature.reports.data

import android.content.Context
import android.net.Uri
import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class ReportFileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ReportFileRepository {
    override suspend fun writeCsv(uri: String, rows: List<CsvReportRow>, includeNotes: Boolean): Boolean =
        write(uri) { CsvReportWriter.write(it, rows, includeNotes) }

    override suspend fun writePdf(uri: String, data: PdfReportData): Boolean = write(uri) { PdfReportRenderer().render(data, it) }

    private suspend fun write(uri: String, block: (OutputStream) -> Unit): Boolean = withContext(dispatcher) {
        try {
            val out = context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: return@withContext false
            out.use(block)
            true
        } catch (_: IOException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}
