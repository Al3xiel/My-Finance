package com.al3xiel.my_finances.feature.diagnostics.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.WorkspaceSummary

@Composable
fun DiagnosticWorkspaceContent(
    state: DiagnosticWorkspaceUiState,
    onAction: (DiagnosticWorkspaceUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.diagnostic_workspace_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.diagnostic_workspace_privacy_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error) }
        val summary = state.summary
        when {
            state.stage == WorkspaceStage.LOCKED -> Unit
            state.stage == WorkspaceStage.LOADING || state.stage == WorkspaceStage.WORKING -> BusyRow()
            summary == null -> EmptySection(onAction)
            else -> {
                SummarySection(summary)
                state.report?.let { ReportSection(it) }
                RequestSection(summary, state, onAction)
                OutlinedButton(onClick = { onAction(DiagnosticWorkspaceUiAction.ApplyDictionary) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.diagnostic_workspace_dictionary_apply))
                }
                if (state.names.isNotEmpty()) NamesSection(state.names)
                TextButton(onClick = { onAction(DiagnosticWorkspaceUiAction.AskDelete) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.diagnostic_workspace_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    WorkspaceDialogs(state, onAction)
}

@Composable
private fun BusyRow() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.diagnostic_workspace_working))
    }
}

@Composable
private fun EmptySection(onAction: (DiagnosticWorkspaceUiAction) -> Unit) {
    Text(stringResource(R.string.diagnostic_workspace_empty))
    Button(onClick = { onAction(DiagnosticWorkspaceUiAction.OpenPackage) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.diagnostic_workspace_open))
    }
}

@Composable
private fun SummarySection(summary: WorkspaceSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.diagnostic_workspace_summary_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.diagnostic_workspace_summary_scope, summary.scope, summary.eventCount))
        Text(stringResource(R.string.diagnostic_workspace_summary_version, summary.appVersion, summary.androidVersion))
        Text(stringResource(R.string.diagnostic_workspace_summary_exported, summary.exportedAt))
        Text(stringResource(R.string.diagnostic_workspace_summary_hash, summary.packageHashPrefix))
        Text(
            if (summary.anonymized) stringResource(R.string.diagnostic_workspace_summary_anonymized, summary.alteredEventCount)
            else stringResource(R.string.diagnostic_workspace_summary_not_anonymized),
        )
    }
}

@Composable
private fun RequestSection(summary: WorkspaceSummary, state: DiagnosticWorkspaceUiState, onAction: (DiagnosticWorkspaceUiAction) -> Unit) {
    if (summary.pseudonyms.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.diagnostic_workspace_request_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.diagnostic_workspace_request_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        summary.pseudonyms.forEach { pseudonym ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = pseudonym in state.requestSelection,
                    onCheckedChange = { onAction(DiagnosticWorkspaceUiAction.ToggleRequestPseudonym(pseudonym)) },
                )
                Text(state.names[pseudonym]?.let { "$pseudonym → $it" } ?: pseudonym)
            }
        }
        Button(
            onClick = { onAction(DiagnosticWorkspaceUiAction.CreateRequest) },
            enabled = state.requestSelection.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.diagnostic_workspace_request_create)) }
    }
}

@Composable
private fun NamesSection(names: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.diagnostic_workspace_dictionary_names), style = MaterialTheme.typography.titleMedium)
        names.toSortedMap().forEach { (pseudonym, alias) -> Text("$pseudonym → $alias") }
    }
}

private fun WorkspaceMessage.textRes() = when (this) {
    WorkspaceMessage.AUTH_FAILED -> R.string.diagnostic_workspace_error_auth
    WorkspaceMessage.ALREADY_OPEN -> R.string.diagnostic_workspace_error_already_open
    WorkspaceMessage.NOT_A_PACKAGE -> R.string.diagnostic_workspace_error_not_a_package
    WorkspaceMessage.UNSUPPORTED_PACKAGE -> R.string.diagnostic_workspace_error_unsupported
    WorkspaceMessage.WRONG_PASSWORD -> R.string.diagnostic_workspace_error_password
    WorkspaceMessage.READ_FAILED -> R.string.diagnostic_workspace_error_read
    WorkspaceMessage.DICTIONARY_MISMATCH -> R.string.diagnostic_workspace_error_dictionary_mismatch
    WorkspaceMessage.DICTIONARY_INVALID -> R.string.diagnostic_workspace_error_dictionary_invalid
    WorkspaceMessage.UNEXPECTED -> R.string.diagnostic_workspace_error_unexpected
}

private val previewSummary = WorkspaceSummary(
    "x1", "RECONCILIATION", true, 42, "2026-11-02T12:00:00Z", "1.0", "14", 12, "abcdef012345", listOf("Account-A", "Person-1"),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiagnosticWorkspaceContentPreview() {
    DiagnosticWorkspaceContent(
        DiagnosticWorkspaceUiState(stage = WorkspaceStage.READY, summary = previewSummary, names = mapOf("Account-A" to "Cuenta")),
        {},
    )
}

@Preview(showBackground = true)
@Composable
private fun DiagnosticWorkspaceEmptyPreview() {
    DiagnosticWorkspaceContent(DiagnosticWorkspaceUiState(stage = WorkspaceStage.EMPTY), {})
}
