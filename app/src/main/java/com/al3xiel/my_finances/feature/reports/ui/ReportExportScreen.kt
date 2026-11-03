package com.al3xiel.my_finances.feature.reports.ui

import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.PillButton
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.feature.reports.domain.model.ReportExportTexts
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.ui.components.periodLabel
import java.time.YearMonth
import java.util.Locale

/** S45: formato, "incluir notas", aviso "no cifrado" y selector del sistema (`CreateDocument`, sin archivo temporal). */
@Composable
fun ReportExportScreen(
    from: String,
    to: String,
    modifier: Modifier = Modifier,
    viewModel: ReportExportViewModel = hiltViewModel<ReportExportViewModel, ReportExportViewModel.Factory>(
        key = "$from..$to",
        creationCallback = { it.create(from, to) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val texts = rememberExportTexts(state.period)
    val launcher = rememberLauncherForActivityResult(remember(state.format) { ActivityResultContracts.CreateDocument(state.format.mimeType) }) { uri ->
        viewModel.onAction(ReportExportUiAction.PickerResult(uri?.toString(), texts))
    }
    LaunchedEffect(state.phase) {
        if (state.phase == ExportPhase.PICKING) {
            viewModel.onAction(ReportExportUiAction.PickerOpened)
            launcher.launch(state.fileName)
        }
    }
    ReportExportContent(state, viewModel::onAction, modifier)
}

@Composable
private fun rememberExportTexts(period: ReportPeriod) = ReportExportTexts(
    title = stringResource(R.string.report_export_pdf_title),
    periodLabel = periodLabel(period, Locale.getDefault()),
    notice = stringResource(R.string.report_export_pdf_notice),
    summaryHeading = stringResource(R.string.report_export_pdf_summary),
    categoryHeading = stringResource(R.string.report_summary_by_category),
    tagHeading = stringResource(R.string.report_summary_by_tag),
    accountHeading = stringResource(R.string.report_summary_by_account),
    income = stringResource(R.string.report_summary_income),
    ownSpend = stringResource(R.string.report_summary_own_spend),
    othersTotal = stringResource(R.string.report_summary_others) + ": " + stringResource(R.string.report_summary_others_total),
    othersReimbursed = stringResource(R.string.report_summary_others_reimbursed),
    othersPending = stringResource(R.string.report_summary_others_pending),
    unidentified = stringResource(R.string.report_summary_unidentified),
    columnName = stringResource(R.string.report_export_pdf_name),
    columnAmount = stringResource(R.string.report_export_pdf_amount),
    columnShare = stringResource(R.string.report_export_pdf_share),
    columnInflow = stringResource(R.string.report_by_account_inflow),
    columnOutflow = stringResource(R.string.report_by_account_outflow),
    columnTransfers = stringResource(R.string.report_by_account_transfers),
    originHeading = stringResource(R.string.report_export_pdf_origin_composition),
    originSpendHeading = stringResource(R.string.report_export_pdf_origin_spend),
    estimated = stringResource(R.string.origin_estimated_label).lowercase(),
    columnSource = stringResource(R.string.report_origin_spend_pick),
    columnTarget = stringResource(R.string.report_export_pdf_target),
    loans = stringResource(R.string.report_origin_target_loans),
    unidentifiedTarget = stringResource(R.string.report_origin_target_unidentified),
)

@Composable
fun ReportExportContent(
    state: ReportExportUiState,
    onAction: (ReportExportUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.report_export_title), modifier = modifier) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(periodLabel(state.period, Locale.getDefault()), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.report_export_format), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FilterChip(
                    selected = state.format == ExportFormat.CSV,
                    onClick = { onAction(ReportExportUiAction.SetFormat(ExportFormat.CSV)) },
                    label = { Text(stringResource(R.string.report_export_csv)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                FilterChip(
                    selected = state.format == ExportFormat.PDF,
                    onClick = { onAction(ReportExportUiAction.SetFormat(ExportFormat.PDF)) },
                    label = { Text(stringResource(R.string.report_export_pdf)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            if (state.format == ExportFormat.CSV) {
                SectionCard {
                    SectionRow(
                        headline = stringResource(R.string.report_export_include_notes),
                        supporting = stringResource(R.string.report_export_include_notes_hint),
                        trailing = {
                            Switch(checked = state.includeNotes, onCheckedChange = { onAction(ReportExportUiAction.SetIncludeNotes(it)) })
                        },
                    )
                }
            }
            PillButton(
                stringResource(R.string.report_export_action),
                { onAction(ReportExportUiAction.Export) },
                Modifier.fillMaxWidth(),
                enabled = state.phase == ExportPhase.IDLE || state.phase == ExportPhase.DONE || state.phase == ExportPhase.FAILED,
            )
            when (state.phase) {
                ExportPhase.WRITING -> Text(stringResource(R.string.report_export_writing))
                ExportPhase.DONE -> Text(stringResource(R.string.report_export_done), color = MaterialTheme.colorScheme.primary)
                ExportPhase.FAILED -> Text(stringResource(R.string.report_export_failed), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }
    if (state.phase == ExportPhase.NOTICE) {
        AlertDialog(
            onDismissRequest = { onAction(ReportExportUiAction.DismissNotice) },
            title = { Text(stringResource(R.string.report_export_notice_title)) },
            text = { Text(stringResource(R.string.report_export_notice_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(ReportExportUiAction.AcceptNotice) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.report_export_notice_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ReportExportUiAction.DismissNotice) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReportExportContentPreview() {
    MyfinancesTheme { ReportExportContent(ReportExportUiState(ReportPeriod.Month(YearMonth.of(2026, 10))), onAction = {}) }
}
