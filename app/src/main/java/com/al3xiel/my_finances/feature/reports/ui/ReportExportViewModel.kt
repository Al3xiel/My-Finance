package com.al3xiel.my_finances.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.formatBasisPoints
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.reports.domain.model.CsvReportRow
import com.al3xiel.my_finances.feature.reports.domain.model.PdfReportData
import com.al3xiel.my_finances.feature.reports.domain.model.PdfSection
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportFileRepository
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportRepository
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf"),
}

/** Dónde va el flujo: aviso "no cifrado" → selector del sistema → escritura. Cancelar en cualquier punto no deja nada. */
enum class ExportPhase { IDLE, NOTICE, PICKING, WRITING, DONE, FAILED }

data class ReportExportUiState(
    val period: ReportPeriod,
    val format: ExportFormat = ExportFormat.CSV,
    val includeNotes: Boolean = false,
    val phase: ExportPhase = ExportPhase.IDLE,
) {
    val fileName: String get() = "reporte-${period.from}_${period.to}.${format.extension}"
}

/** Textos del PDF, resueltos por la pantalla (el ViewModel no conoce `Context`). */
data class ReportExportTexts(
    val title: String,
    val periodLabel: String,
    val notice: String,
    val summaryHeading: String,
    val categoryHeading: String,
    val tagHeading: String,
    val accountHeading: String,
    val income: String,
    val ownSpend: String,
    val othersTotal: String,
    val othersReimbursed: String,
    val othersPending: String,
    val unidentified: String,
    val columnName: String,
    val columnAmount: String,
    val columnShare: String,
    val columnInflow: String,
    val columnOutflow: String,
    val columnTransfers: String,
)

sealed interface ReportExportUiAction {
    data class SetFormat(val format: ExportFormat) : ReportExportUiAction
    data class SetIncludeNotes(val include: Boolean) : ReportExportUiAction
    data object Export : ReportExportUiAction
    data object AcceptNotice : ReportExportUiAction
    data object DismissNotice : ReportExportUiAction

    /** `uri` nulo = el usuario canceló el selector. */
    data class PickerResult(val uri: String?, val texts: ReportExportTexts) : ReportExportUiAction
    data object Dismiss : ReportExportUiAction
}

/** S45: exportar el periodo a CSV o PDF (D38, docs/02 RX1–RX4). */
@HiltViewModel(assistedFactory = ReportExportViewModel.Factory::class)
class ReportExportViewModel @AssistedInject constructor(
    @Assisted("from") from: String,
    @Assisted("to") to: String,
    private val reports: ReportRepository,
    private val accounts: AccountRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val files: ReportFileRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("from") from: String, @Assisted("to") to: String): ReportExportViewModel
    }

    private val period = ReportPeriod.fromTexts(from, to)
    private val _uiState = MutableStateFlow(ReportExportUiState(period))
    val uiState: StateFlow<ReportExportUiState> = _uiState

    fun onAction(action: ReportExportUiAction) {
        when (action) {
            is ReportExportUiAction.SetFormat -> _uiState.update { it.copy(format = action.format) }
            is ReportExportUiAction.SetIncludeNotes -> _uiState.update { it.copy(includeNotes = action.include) }
            ReportExportUiAction.Export -> _uiState.update { it.copy(phase = ExportPhase.NOTICE) }
            ReportExportUiAction.AcceptNotice -> _uiState.update { it.copy(phase = ExportPhase.PICKING) }
            ReportExportUiAction.DismissNotice, ReportExportUiAction.Dismiss -> _uiState.update { it.copy(phase = ExportPhase.IDLE) }
            is ReportExportUiAction.PickerResult ->
                if (action.uri == null) _uiState.update { it.copy(phase = ExportPhase.IDLE) } else write(action.uri, action.texts)
        }
    }

    private fun write(uri: String, texts: ReportExportTexts) {
        val state = _uiState.value
        _uiState.update { it.copy(phase = ExportPhase.WRITING) }
        viewModelScope.launch {
            val ok = when (state.format) {
                ExportFormat.CSV -> {
                    files.writeCsv(uri, csvRows(), state.includeNotes)
                }
                ExportFormat.PDF -> {
                    files.writePdf(uri, pdfData(texts))
                }
            }
            _uiState.update { it.copy(phase = if (ok) ExportPhase.DONE else ExportPhase.FAILED) }
        }
    }

    private suspend fun csvRows(): List<CsvReportRow> {
        val parts = reports.parts(period)
        val details = reports.partDetails(period)
        val accountNames = accounts.observeAccounts().first().associate { it.account.id.value to it.account.name }
        val categoryNames = categories.observeAll().first().associate { it.id.value to it.name }
        val tagNames = tags.observeAll().first().associate { it.id.value to it.name }
        return parts.filter { it.ownAmount > 0 }.mapNotNull { p ->
            val d = details[p.partId] ?: return@mapNotNull null
            CsvReportRow(
                date = d.date,
                account = accountNames[p.accountId] ?: p.accountId,
                category = categoryNames[p.categoryId] ?: p.categoryId,
                tags = p.tagIds.map { tagNames[it] ?: it },
                ownAmountMinor = p.ownAmount,
                type = if (d.kind == PartKind.NORMAL) "EXPENSE" else d.kind.name,
                note = d.note,
            )
        }.sortedWith(compareBy({ it.date }, { it.account }, { it.category }))
    }

    private suspend fun pdfData(t: ReportExportTexts): PdfReportData {
        val parts = reports.parts(period)
        val ledger = reports.ledger(period)
        val accountNames = accounts.observeAccounts().first().associate { it.account.id.value to it.account.name }
        val categoryNames = categories.observeAll().first().associate { it.id.value to it.name }
        val tagNames = tags.observeAll().first().associate { it.id.value to it.name }
        val summary = ReportCalculator.summary(parts, ledger)
        fun money(minor: Long) = MoneyFormatter.format(Money(minor, CurrencyCode.PEN))
        val summaryRows = listOf(
            listOf(t.income, money(summary.income)),
            listOf(t.ownSpend, money(summary.ownSpend)),
            listOf(t.othersTotal, money(summary.others.total)),
            listOf(t.othersReimbursed, money(summary.others.reimbursed)),
            listOf(t.othersPending, money(summary.others.pending)),
            listOf(t.unidentified, money(summary.unidentified)),
        )
        return PdfReportData(
            title = t.title,
            subtitle = t.periodLabel,
            notice = t.notice,
            sections = listOf(
                PdfSection(t.summaryHeading, listOf(t.columnName, t.columnAmount), summaryRows),
                PdfSection(
                    t.categoryHeading, listOf(t.columnName, t.columnAmount, t.columnShare),
                    ReportCalculator.byCategory(parts).map { listOf(categoryNames[it.categoryId] ?: it.categoryId, money(it.amount), formatBasisPoints(it.basisPoints)) },
                ),
                PdfSection(
                    t.tagHeading, listOf(t.columnName, t.columnAmount),
                    ReportCalculator.byTag(parts).map { listOf(tagNames[it.tagId] ?: it.tagId, money(it.amount)) },
                ),
                PdfSection(
                    t.accountHeading, listOf(t.columnName, t.columnInflow, t.columnOutflow, t.columnTransfers),
                    ReportCalculator.byAccount(ledger).map {
                        listOf(accountNames[it.accountId] ?: it.accountId, money(it.inflow), money(it.outflow), money(it.transfersNet))
                    },
                ),
            ),
        )
    }
}
