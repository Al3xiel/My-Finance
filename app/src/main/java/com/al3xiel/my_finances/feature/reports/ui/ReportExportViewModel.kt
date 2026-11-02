package com.al3xiel.my_finances.feature.reports.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.model.ReportExportTexts
import com.al3xiel.my_finances.feature.reports.domain.repository.ReportFileRepository
import com.al3xiel.my_finances.feature.reports.domain.usecase.BuildReportDocumentsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    PDF("pdf", "application/pdf"),
}

/** Dónde va el flujo: aviso "no cifrado" → selector del sistema → escritura. Cancelar en cualquier punto no deja nada. */
enum class ExportPhase { IDLE, NOTICE, PICKING, PICKER_OPEN, WRITING, DONE, FAILED }

data class ReportExportUiState(
    val period: ReportPeriod,
    val format: ExportFormat = ExportFormat.CSV,
    val includeNotes: Boolean = false,
    val phase: ExportPhase = ExportPhase.IDLE,
) {
    val fileName: String get() = "reporte-${period.from}_${period.to}.${format.extension}"
}

sealed interface ReportExportUiAction {
    data class SetFormat(val format: ExportFormat) : ReportExportUiAction
    data class SetIncludeNotes(val include: Boolean) : ReportExportUiAction
    data object Export : ReportExportUiAction
    data object AcceptNotice : ReportExportUiAction

    /** La pantalla ya abrió el selector: si se recrea, no lo abre otra vez. */
    data object PickerOpened : ReportExportUiAction

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
    private val documents: BuildReportDocumentsUseCase,
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
            ReportExportUiAction.PickerOpened -> _uiState.update { if (it.phase == ExportPhase.PICKING) it.copy(phase = ExportPhase.PICKER_OPEN) else it }
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
                    files.writeCsv(uri, documents.csvRows(period), state.includeNotes)
                }
                ExportFormat.PDF -> {
                    files.writePdf(uri, documents.pdf(period, texts))
                }
            }
            _uiState.update { it.copy(phase = if (ok) ExportPhase.DONE else ExportPhase.FAILED) }
        }
    }
}
