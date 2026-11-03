package com.al3xiel.my_finances.feature.diagnostics.ui

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DictionaryScope

/** S65: lanza el selector del sistema (`CreateDocument`); el resto vive en [DictionaryExportContent]. */
@Composable
fun DictionaryExportScreen(
    reconciliationId: ReconciliationId,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DictionaryExportViewModel = hiltViewModel<DictionaryExportViewModel, DictionaryExportViewModel.Factory>(
        key = reconciliationId.value,
        creationCallback = { it.create(reconciliationId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(KEY_MIME_TYPE)) { uri ->
        viewModel.onAction(DictionaryExportUiAction.PickerResult(uri?.toString()))
    }
    LaunchedEffect(state.stage) {
        if (state.stage == DictionaryStage.PICKING) {
            viewModel.onAction(DictionaryExportUiAction.PickerOpened)
            launcher.launch("diccionario.fkey")
        }
    }
    DictionaryExportContent(state, viewModel::onAction, onFinished, modifier)
}

@Composable
fun DictionaryExportContent(
    state: DictionaryExportUiState,
    onAction: (DictionaryExportUiAction) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val prompt = AuthPrompt(stringResource(R.string.dictionary_export_auth_title), stringResource(R.string.dictionary_export_auth_subtitle))
    val busy = state.stage != DictionaryStage.FORM

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.dictionary_export_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.dictionary_export_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.stage == DictionaryStage.DONE) {
            Text(stringResource(R.string.dictionary_export_done_title), style = MaterialTheme.typography.titleMedium)
            state.created?.let { Text(stringResource(R.string.dictionary_export_done_body, it.revealedPseudonyms, it.revealedNotes)) }
            Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dictionary_export_done_ok)) }
            return@Column
        }
        OutlinedTextField(
            value = state.requestCode,
            onValueChange = { onAction(DictionaryExportUiAction.SetRequestCode(it)) },
            label = { Text(stringResource(R.string.dictionary_export_code_label)) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.scope == DictionaryScope.PARTIAL, { onAction(DictionaryExportUiAction.SetScope(DictionaryScope.PARTIAL)) }, { Text(stringResource(R.string.dictionary_export_scope_partial)) }, enabled = !busy)
            FilterChip(state.scope == DictionaryScope.FULL, { onAction(DictionaryExportUiAction.SetScope(DictionaryScope.FULL)) }, { Text(stringResource(R.string.dictionary_export_scope_full)) }, enabled = !busy)
        }
        state.preview?.let { PreviewSection(it) }
        PasswordField(password, R.string.dictionary_export_password_label) { password = it }
        PasswordField(confirmation, R.string.dictionary_export_password_confirm_label) { confirmation = it }
        state.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                onAction(DictionaryExportUiAction.Submit(password.toCharArray(), confirmation.toCharArray(), prompt))
                password = ""
                confirmation = ""
            },
            enabled = !busy && state.preview != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.stage == DictionaryStage.WRITING) R.string.dictionary_export_writing else R.string.dictionary_export_button)) }
    }
}

@Composable
private fun PreviewSection(preview: DictionaryPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.dictionary_export_preview_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.dictionary_export_preview_pseudonyms, preview.pseudonyms.size, preview.pseudonyms.joinToString(", ")))
        if (preview.noteEventIds.isNotEmpty()) Text(stringResource(R.string.dictionary_export_preview_notes, preview.noteEventIds.size))
        if (preview.unknown > 0) Text(stringResource(R.string.dictionary_export_preview_unknown, preview.unknown), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasswordField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun DictionaryMessage.textRes() = when (this) {
    DictionaryMessage.INVALID_REQUEST -> R.string.dictionary_export_error_invalid
    DictionaryMessage.PACKAGE_MISMATCH -> R.string.dictionary_export_error_mismatch
    DictionaryMessage.NOT_FROM_RECONCILIATION -> R.string.dictionary_export_error_reconciliation
    DictionaryMessage.PASSWORD_TOO_SHORT -> R.string.dictionary_export_error_password_short
    DictionaryMessage.PASSWORD_TOO_LONG -> R.string.dictionary_export_error_password_long
    DictionaryMessage.PASSWORD_MISMATCH -> R.string.dictionary_export_error_password_mismatch
    DictionaryMessage.AUTH_FAILED -> R.string.dictionary_export_error_auth
    DictionaryMessage.CREATE_FAILED -> R.string.dictionary_export_error_create
    DictionaryMessage.VERIFY_FAILED -> R.string.dictionary_export_error_verify
}

private const val KEY_MIME_TYPE = "application/octet-stream"

private val previewState = DictionaryExportUiState(
    requestCode = "eyJleHBvcnRJZCI6IngifQ.1a2b3c4d",
    preview = DictionaryPreview(listOf("Account-A", "Person-1"), listOf("e4"), 1),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DictionaryExportContentPreview() {
    DictionaryExportContent(previewState, {}, {})
}
