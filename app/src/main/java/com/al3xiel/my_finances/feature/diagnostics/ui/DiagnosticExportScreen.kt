package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.core.designsystem.BlockedHint
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.PillButton
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.PasswordField
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticResult
import java.time.LocalDate

/** S63: lanza el selector del sistema (`CreateDocument`); el resto vive en [DiagnosticExportContent]. */
@Composable
fun DiagnosticExportScreen(
    reconciliationId: ReconciliationId?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiagnosticExportViewModel = hiltViewModel<DiagnosticExportViewModel, DiagnosticExportViewModel.Factory>(
        key = reconciliationId?.value ?: "full",
        creationCallback = { it.create(reconciliationId?.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(DIAGNOSTIC_MIME_TYPE)) { uri ->
        viewModel.onAction(DiagnosticExportUiAction.PickerResult(uri?.toString()))
    }
    LaunchedEffect(state.stage) {
        if (state.stage == ExportStage.PICKING) {
            viewModel.onAction(DiagnosticExportUiAction.PickerOpened)
            launcher.launch("diagnostico-${LocalDate.now()}.fdiag")
        }
    }
    DiagnosticExportContent(state, viewModel::onAction, onFinished, modifier)
}

@Composable
fun DiagnosticExportContent(
    state: DiagnosticExportUiState,
    onAction: (DiagnosticExportUiAction) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val prompt = AuthPrompt(stringResource(R.string.diagnostic_export_auth_title), stringResource(R.string.diagnostic_export_auth_subtitle))
    val busy = state.stage != ExportStage.FORM && state.stage != ExportStage.DONE

    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.diagnostic_export_title), modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(stringResource(R.string.diagnostic_export_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.stage == ExportStage.DONE) {
                DoneSection(state.created, onFinished)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                state.availableScopes.forEach { scope ->
                    FilterChip(
                        selected = state.scope == scope,
                        onClick = { onAction(DiagnosticExportUiAction.SetScope(scope)) },
                        label = { Text(stringResource(scope.labelRes())) },
                        enabled = !busy,
                    )
                }
            }
            PreviewSection(state.preview)
            SectionCard {
                ToggleRow(R.string.diagnostic_export_anonymize, state.anonymize, !busy) { onAction(DiagnosticExportUiAction.SetAnonymize(it)) }
                SectionDivider()
                ToggleRow(R.string.diagnostic_export_password_toggle, state.usePassword, !busy) { onAction(DiagnosticExportUiAction.SetUsePassword(it)) }
            }
            if (!state.anonymize) Text(stringResource(R.string.diagnostic_export_not_anonymized_warning), color = MaterialTheme.colorScheme.error)
            if (state.usePassword) {
                PasswordField(password, R.string.diagnostic_export_password_label, Modifier.fillMaxWidth()) { password = it }
                PasswordField(confirmation, R.string.diagnostic_export_password_confirm_label, Modifier.fillMaxWidth()) { confirmation = it }
            }
            state.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error) }
            val blocked = !busy && state.preview == null
            BlockedHint(stringResource(R.string.diagnostic_export_blocked_preview).takeIf { blocked })
            PillButton(
                text = stringResource(if (state.stage == ExportStage.WRITING) R.string.diagnostic_export_writing else R.string.diagnostic_export_button),
                onClick = {
                    onAction(DiagnosticExportUiAction.Submit(password.toCharArray(), confirmation.toCharArray(), prompt))
                    password = ""
                    confirmation = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && state.preview != null,
                loading = state.stage == ExportStage.WRITING,
            )
        }
    }
}

@Composable
private fun PreviewSection(preview: ExportPreview?) {
    if (preview == null) return
    SectionCard(title = stringResource(R.string.diagnostic_export_preview_title)) {
        Column(Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.diagnostic_export_preview_events, preview.eventCount))
            if (preview.firstOccurredOn != null && preview.lastOccurredOn != null) {
                Text(stringResource(R.string.diagnostic_export_preview_range, preview.firstOccurredOn.toString(), preview.lastOccurredOn.toString()))
            }
            Text(stringResource(R.string.diagnostic_export_preview_fields, preview.anonymizedFields.size))
        }
    }
}

@Composable
private fun DoneSection(created: ExportDiagnosticResult.Created?, onFinished: () -> Unit) {
    SectionCard {
        Column(Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.diagnostic_export_done_title), style = MaterialTheme.typography.titleMedium)
            created?.let { Text(stringResource(R.string.diagnostic_export_done_body, it.eventCount, it.packageHash.take(HASH_PREFIX))) }
        }
    }
    PillButton(stringResource(R.string.diagnostic_export_done_ok), onFinished, Modifier.fillMaxWidth())
}

@Composable
private fun ToggleRow(label: Int, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SectionRow(
        headline = stringResource(label),
        modifier = Modifier.toggleable(value = checked, onValueChange = onChange, enabled = enabled, role = Role.Switch),
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    )
}

private fun DiagnosticScope.labelRes() = when (this) {
    DiagnosticScope.RECONCILIATION -> R.string.diagnostic_export_scope_reconciliation
    DiagnosticScope.ACCOUNT_HISTORY -> R.string.diagnostic_export_scope_account_history
    DiagnosticScope.FULL -> R.string.diagnostic_export_scope_full
}

private fun ExportMessage.textRes() = when (this) {
    ExportMessage.NOT_FOUND -> R.string.error_not_found
    ExportMessage.PASSWORD_TOO_SHORT -> R.string.diagnostic_export_error_password_short
    ExportMessage.PASSWORD_TOO_LONG -> R.string.diagnostic_export_error_password_long
    ExportMessage.PASSWORD_MISMATCH -> R.string.diagnostic_export_error_password_mismatch
    ExportMessage.AUTH_FAILED -> R.string.diagnostic_export_error_auth
    ExportMessage.CREATE_FAILED -> R.string.diagnostic_export_error_create
    ExportMessage.VERIFY_FAILED -> R.string.diagnostic_export_error_verify
    ExportMessage.UNEXPECTED -> R.string.diagnostic_export_error_unexpected
}

private const val DIAGNOSTIC_MIME_TYPE = "application/octet-stream"
private const val HASH_PREFIX = 12

private val previewState = DiagnosticExportUiState(
    scope = DiagnosticScope.RECONCILIATION,
    availableScopes = listOf(DiagnosticScope.RECONCILIATION, DiagnosticScope.ACCOUNT_HISTORY, DiagnosticScope.FULL),
    preview = ExportPreview(42, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 2), listOf("a", "b", "c")),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun DiagnosticExportContentPreview() {
    MyfinancesTheme { DiagnosticExportContent(previewState.copy(usePassword = true), {}, {}) }
}
