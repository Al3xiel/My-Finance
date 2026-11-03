package com.al3xiel.my_finances.feature.diagnostics.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.feature.diagnostics.domain.model.FingerprintChange
import com.al3xiel.my_finances.feature.diagnostics.domain.model.RawEventLine

private const val PAYLOAD_PREVIEW = 160

/** S73: pide la credencial al entrar y deja el resto a [DevToolsContent]. `onOpenPackage` lleva a S64. */
@Composable
fun DevToolsScreen(onOpenPackage: () -> Unit, modifier: Modifier = Modifier, viewModel: DevToolsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prompt = AuthPrompt(stringResource(R.string.devtools_auth_title), stringResource(R.string.devtools_auth_subtitle))
    LaunchedEffect(Unit) { viewModel.onAction(DevToolsUiAction.Authenticate(prompt)) }
    DevToolsContent(state, viewModel::onAction, onOpenPackage, prompt, modifier)
}

@Composable
fun DevToolsContent(
    state: DevToolsUiState,
    onAction: (DevToolsUiAction) -> Unit,
    onOpenPackage: () -> Unit,
    prompt: AuthPrompt,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.devtools_title), style = MaterialTheme.typography.titleLarge)
        state.message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error) }
        if (state.stage == DevToolsStage.LOCKED) {
            OutlinedButton(onClick = { onAction(DevToolsUiAction.Authenticate(prompt)) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.devtools_unlock))
            }
            return@Column
        }
        val enabled = state.stage == DevToolsStage.READY
        Button(onClick = { onAction(DevToolsUiAction.VerifyChain) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.devtools_verify_chain))
        }
        state.chain?.let { Text(it.text()) }
        Button(onClick = { onAction(DevToolsUiAction.Replay) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.devtools_replay))
        }
        state.changes?.let { ChangesSection(it) }
        Text(stringResource(R.string.devtools_needs_review, state.needsReviewCount))
        OutlinedButton(onClick = onOpenPackage, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.devtools_open_package))
        }
        EventsSection(state, onAction)
    }
}

@Composable
private fun ChainVerification.text(): String = when (this) {
    is ChainVerification.Valid -> stringResource(R.string.devtools_chain_valid, eventCount)
    is ChainVerification.Broken -> stringResource(R.string.devtools_chain_broken, sequence, reason.name)
}

@Composable
private fun ChangesSection(changes: List<FingerprintChange>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.devtools_changes_title), style = MaterialTheme.typography.titleMedium)
        val changed = changes.filter { it.changed }
        if (changed.isEmpty()) {
            Text(stringResource(R.string.devtools_changes_none))
        } else {
            val missing = stringResource(R.string.devtools_missing)
            changed.forEach {
                Text(stringResource(R.string.devtools_changes_row, it.table, it.beforeRows?.toString() ?: missing, it.afterRows?.toString() ?: missing))
            }
        }
    }
}

@Composable
private fun EventsSection(state: DevToolsUiState, onAction: (DevToolsUiAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.devtools_events_title), style = MaterialTheme.typography.titleMedium)
        state.events.forEach { EventRow(it) }
        if (state.hasMoreEvents) {
            OutlinedButton(onClick = { onAction(DevToolsUiAction.LoadMoreEvents) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.devtools_events_more))
            }
        }
    }
}

@Composable
private fun EventRow(event: RawEventLine) {
    Column {
        Text("#${event.sequence} · ${event.type} · ${event.occurredOn}", style = MaterialTheme.typography.labelLarge)
        Text(event.payload.take(PAYLOAD_PREVIEW), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DevToolsMessage.textRes() = when (this) {
    DevToolsMessage.AUTH_FAILED -> R.string.devtools_error_auth
    DevToolsMessage.NO_CREDENTIAL -> R.string.devtools_error_no_credential
    DevToolsMessage.UNEXPECTED -> R.string.devtools_error_unexpected
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DevToolsContentPreview() {
    DevToolsContent(
        DevToolsUiState(
            stage = DevToolsStage.READY,
            chain = ChainVerification.Valid(120),
            changes = listOf(FingerprintChange("ledger_entries", 10, 11, true)),
            events = listOf(RawEventLine(1, "AccountCreated", "ACCOUNT", "a1", "2026-10-01", "{\"accountId\":\"a1\"}")),
            hasMoreEvents = true,
            needsReviewCount = 1,
        ),
        {}, {}, AuthPrompt("Confirma"),
    )
}
