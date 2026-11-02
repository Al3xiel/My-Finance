package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.reports.domain.model.PartDetail
import com.al3xiel.my_finances.feature.reports.domain.model.PartRow
import com.al3xiel.my_finances.feature.reports.data.CsvReportWriter
import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData
import com.al3xiel.my_finances.feature.reports.domain.model.ReportExportTexts
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportFileRepository
import com.al3xiel.my_finances.feature.reports.domain.usecase.BuildReportDocumentsUseCase
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReportExportViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeFiles(var succeeds: Boolean = true) : ReportFileRepository {
        val writes = mutableListOf<String>()
        val content = ByteArrayOutputStream()
        override suspend fun writeCsv(uri: String, rows: List<CsvReportRow>, includeNotes: Boolean): Boolean {
            writes += uri
            CsvReportWriter.write(content, rows, includeNotes)
            return succeeds
        }

        override suspend fun writePdf(uri: String, data: PdfReportData): Boolean {
            writes += uri
            return succeeds
        }
    }

    private val f = MovementFixture()
    private val reports = FakeReportRepository()
    private val texts = ReportExportTexts("T", "P", "N", "S", "C", "Tg", "A", "i", "o", "t", "r", "p", "u", "n", "m", "%", "in", "out", "tr", "or", "os", "es", "src", "tgt", "loans", "unid")

    private fun viewModel(sink: FakeFiles) =
        ReportExportViewModel("2026-10-01", "2026-10-31", BuildReportDocumentsUseCase(reports, f.accounts, f.categories, f.tags, FakeOriginRepository(), f.sources), sink)

    private fun withData() {
        reports.parts = listOf(PartRow("p1", "bbva", "food", null, 1_250, false, 0, listOf("t1")))
        reports.details = mapOf("p1" to PartDetail(LocalDate.of(2026, 10, 5), PartKind.NORMAL, "=cmd"))
    }

    @Test fun `the notice comes before the picker and cancelling writes nothing`() {
        val sink = FakeFiles()
        val vm = viewModel(sink)
        vm.onAction(ReportExportUiAction.Export)
        assertEquals(ExportPhase.NOTICE, vm.uiState.value.phase)
        vm.onAction(ReportExportUiAction.AcceptNotice)
        assertEquals(ExportPhase.PICKING, vm.uiState.value.phase)
        vm.onAction(ReportExportUiAction.PickerResult(null, texts))
        assertEquals(ExportPhase.IDLE, vm.uiState.value.phase)
        assertTrue(sink.writes.isEmpty())
    }

    @Test fun `declining the notice goes back without a picker`() {
        val vm = viewModel(FakeFiles())
        vm.onAction(ReportExportUiAction.Export)
        vm.onAction(ReportExportUiAction.DismissNotice)
        assertEquals(ExportPhase.IDLE, vm.uiState.value.phase)
    }

    @Test fun `csv is written to the chosen destination without notes unless asked`() {
        withData()
        val sink = FakeFiles()
        val vm = viewModel(sink)
        vm.onAction(ReportExportUiAction.PickerResult("content://doc/1", texts))
        assertEquals(listOf("content://doc/1"), sink.writes)
        assertEquals(ExportPhase.DONE, vm.uiState.value.phase)
        val csv = sink.content.toString(Charsets.UTF_8)
        assertTrue(csv.contains("2026-10-05,BBVA,Comida,Viaje,12.50,EXPENSE"))
        assertFalse(csv.contains("cmd"))
    }

    @Test fun `notes go out only when included and formulas are neutralised`() {
        withData()
        val sink = FakeFiles()
        val vm = viewModel(sink)
        vm.onAction(ReportExportUiAction.SetIncludeNotes(true))
        vm.onAction(ReportExportUiAction.PickerResult("content://doc/1", texts))
        assertTrue(sink.content.toString(Charsets.UTF_8).contains(",'=cmd"))
    }

    @Test fun `a failed write is reported`() {
        withData()
        val vm = viewModel(FakeFiles(succeeds = false))
        vm.onAction(ReportExportUiAction.PickerResult("content://doc/1", texts))
        assertEquals(ExportPhase.FAILED, vm.uiState.value.phase)
    }

    @Test fun `the pdf format builds its file name and writes through the sink`() {
        withData()
        val sink = FakeFiles()
        val vm = viewModel(sink)
        vm.onAction(ReportExportUiAction.SetFormat(ExportFormat.PDF))
        assertEquals("reporte-2026-10-01_2026-10-31.pdf", vm.uiState.value.fileName)
        vm.onAction(ReportExportUiAction.PickerResult("content://doc/2", texts))
        assertEquals(listOf("content://doc/2"), sink.writes)
        assertEquals(ExportPhase.DONE, vm.uiState.value.phase)
    }

    @Test fun `the picker is opened once and a result while it is open writes`() {
        withData()
        val sink = FakeFiles()
        val vm = viewModel(sink)
        vm.onAction(ReportExportUiAction.Export)
        vm.onAction(ReportExportUiAction.AcceptNotice)
        vm.onAction(ReportExportUiAction.PickerOpened)
        assertEquals(ExportPhase.PICKER_OPEN, vm.uiState.value.phase)
        vm.onAction(ReportExportUiAction.PickerOpened)
        assertEquals(ExportPhase.PICKER_OPEN, vm.uiState.value.phase)
        vm.onAction(ReportExportUiAction.PickerResult("content://doc/3", texts))
        assertEquals(ExportPhase.DONE, vm.uiState.value.phase)
    }
}
