package com.al3xiel.my_finances.feature.quickentry.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.feature.quickentry.ui.components.EntryPreviewCard
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S25: escribir una línea, revisar lo entendido y guardar. Al guardar vuelve a la pantalla anterior. */
@Composable
fun QuickEntryScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    QuickEntryContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun QuickEntryContent(
    state: QuickEntryUiState,
    onAction: (QuickEntryUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.quick_entry_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = state.line,
            onValueChange = { onAction(QuickEntryUiAction.EditLine(it)) },
            label = { Text(stringResource(R.string.quick_entry_line_label)) },
            placeholder = { Text(stringResource(R.string.quick_entry_line_hint)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (state.hasLine) {
            EntryPreviewCard(state = state, onAction = onAction)
        }
        state.error?.let {
            Text(
                it.message(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            onClick = { onAction(QuickEntryUiAction.Save) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.editor_save)) }
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(QuickEntryUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(QuickEntryUiAction.DismissWarning) },
        )
    }
}

private val previewState = QuickEntryUiState(
    line = "45 bbva mercado",
    amountText = "45",
    accountId = "a",
    categoryId = "c",
    categorySuggested = true,
    accountOptions = listOf(EntityOption("a", "BBVA")),
    categoryOptions = listOf(EntityOption("c", "Mercado")),
    canSave = true,
)

@Preview(showBackground = true)
@Composable
private fun QuickEntryContentPreview() {
    MyfinancesTheme { QuickEntryContent(previewState, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun QuickEntryContentDarkPreview() {
    MyfinancesTheme { QuickEntryContent(previewState, {}) }
}
