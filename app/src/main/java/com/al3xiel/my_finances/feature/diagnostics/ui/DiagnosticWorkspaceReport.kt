package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DivergenceReport

private const val MAX_ROWS = 20

/** El reporte de divergencias: veredicto, primer evento que difiere, saldos y asientos lado a lado. */
@Composable
internal fun ReportSection(report: DivergenceReport) {
    val missing = stringResource(R.string.diagnostic_workspace_missing)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.diagnostic_workspace_report_title), style = MaterialTheme.typography.titleMedium)
        if (!report.diverges) {
            Text(stringResource(R.string.diagnostic_workspace_report_none))
        } else {
            Text(stringResource(R.string.diagnostic_workspace_report_diverges), color = MaterialTheme.colorScheme.error)
            report.firstDivergentSequence?.let { Text(stringResource(R.string.diagnostic_workspace_report_first, it)) }
        }
        report.balances.filter { it.diverges }.forEach {
            Text(
                stringResource(
                    R.string.diagnostic_workspace_report_balance, it.accountId,
                    it.exportedMinor?.toString() ?: missing, it.recalculatedMinor?.toString() ?: missing,
                ),
            )
        }
        report.reconciliation?.takeIf { it.diverges }?.let {
            Text(
                stringResource(
                    R.string.diagnostic_workspace_report_reconciliation,
                    it.exportedComputedMinor.toString(), it.recalculatedComputedMinor?.toString() ?: missing,
                ),
            )
        }
        report.divergentLedger.take(MAX_ROWS).forEach {
            Text(
                stringResource(
                    R.string.diagnostic_workspace_report_ledger_row, it.sequence,
                    it.exported?.deltaMinor?.toString() ?: missing, it.recalculated?.deltaMinor?.toString() ?: missing,
                ),
            )
        }
        if (report.divergentLedger.size > MAX_ROWS) {
            Text(stringResource(R.string.diagnostic_workspace_report_more, report.divergentLedger.size - MAX_ROWS))
        }
        if (report.versionDifferences.isNotEmpty()) {
            val versions = report.versionDifferences.joinToString { "${it.projector} ${it.exported}→${it.current}" }
            Text(stringResource(R.string.diagnostic_workspace_report_versions, versions))
        }
        report.originDifferences?.takeIf { it.isNotEmpty() }?.let {
            Text(stringResource(R.string.diagnostic_workspace_report_origin, it.size))
        }
    }
}

/** Contraseña, confirmación de borrado y el código de solicitud generado. */
@Composable
internal fun WorkspaceDialogs(state: DiagnosticWorkspaceUiState, onAction: (DiagnosticWorkspaceUiAction) -> Unit) {
    if (state.askPassword != null) PasswordDialog(onAction)
    if (state.askDelete) {
        AlertDialog(
            onDismissRequest = { onAction(DiagnosticWorkspaceUiAction.DismissDelete) },
            title = { Text(stringResource(R.string.diagnostic_workspace_delete_title)) },
            text = { Text(stringResource(R.string.diagnostic_workspace_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(DiagnosticWorkspaceUiAction.ConfirmDelete) }) {
                    Text(stringResource(R.string.diagnostic_workspace_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(DiagnosticWorkspaceUiAction.DismissDelete) }) {
                    Text(stringResource(R.string.diagnostic_workspace_cancel))
                }
            },
        )
    }
    state.requestCode?.let { code ->
        AlertDialog(
            onDismissRequest = { onAction(DiagnosticWorkspaceUiAction.DismissRequest) },
            title = { Text(stringResource(R.string.diagnostic_workspace_request_code)) },
            text = { SelectionContainer { Text(code) } },
            confirmButton = {
                TextButton(onClick = { onAction(DiagnosticWorkspaceUiAction.DismissRequest) }) {
                    Text(stringResource(R.string.diagnostic_workspace_request_close))
                }
            },
        )
    }
}

@Composable
private fun PasswordDialog(onAction: (DiagnosticWorkspaceUiAction) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onAction(DiagnosticWorkspaceUiAction.CancelPassword) },
        title = { Text(stringResource(R.string.diagnostic_workspace_password_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.diagnostic_workspace_password_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onAction(DiagnosticWorkspaceUiAction.SubmitPassword(password.toCharArray()))
                password = ""
            }) { Text(stringResource(R.string.diagnostic_workspace_password_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onAction(DiagnosticWorkspaceUiAction.CancelPassword) }) {
                Text(stringResource(R.string.diagnostic_workspace_cancel))
            }
        },
    )
}
