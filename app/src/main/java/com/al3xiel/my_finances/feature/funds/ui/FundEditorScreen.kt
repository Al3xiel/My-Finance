package com.al3xiel.my_finances.feature.funds.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.al3xiel.my_finances.core.designsystem.DateField
import com.al3xiel.my_finances.core.designsystem.EditorFooter
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import java.time.LocalDate

/** S54: crear un fondo o editar uno; lo que ya no se puede cambiar se muestra bloqueado. */
@Composable
fun FundEditorScreen(
    fundId: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FundEditorViewModel = hiltViewModel<FundEditorViewModel, FundEditorViewModel.Factory>(
        key = fundId ?: "new",
        creationCallback = { it.create(fundId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    FundEditorContent(state, viewModel::onAction, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FundEditorContent(
    state: FundEditorUiState,
    onAction: (FundEditorUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.notFound) {
        EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isEditing) R.string.fund_editor_title_edit else R.string.fund_editor_title_new),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(FundEditorUiAction.EditName(it)) },
            label = { Text(stringResource(R.string.fund_editor_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.fund_editor_mode), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConsumptionMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { onAction(FundEditorUiAction.SelectMode(mode)) },
                    enabled = !state.modeLocked || state.mode == mode,
                    label = { Text(stringResource(mode.labelRes())) },
                )
            }
        }
        if (state.modeLocked) LockedHint()
        if (state.mode == ConsumptionMode.DAILY_ESTIMATE) {
            MoneyInput(
                value = state.estimateText,
                onValueChange = { onAction(FundEditorUiAction.EditEstimate(it)) },
                label = stringResource(R.string.fund_editor_estimate),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.startLocked) {
            Text(stringResource(R.string.fund_editor_starts_on_value, dayText(state.startsOn)))
            LockedHint()
        } else {
            DateField(stringResource(R.string.fund_editor_starts_on), state.startsOn, { onAction(FundEditorUiAction.SetStartsOn(it)) })
        }
        val endsOn = state.endsOn
        if (endsOn == null) {
            TextButton(onClick = { onAction(FundEditorUiAction.SetEndsOn(state.startsOn)) }) {
                Text(stringResource(R.string.fund_editor_ends_on))
            }
        } else {
            DateField(stringResource(R.string.fund_editor_ends_on), endsOn, { onAction(FundEditorUiAction.SetEndsOn(it)) })
            TextButton(onClick = { onAction(FundEditorUiAction.SetEndsOn(null)) }) { Text(stringResource(R.string.fund_editor_no_end)) }
        }
        EditorFooter(error = state.error, onSave = { onAction(FundEditorUiAction.Save) })
    }
}

@Composable
private fun LockedHint() {
    Text(stringResource(R.string.fund_editor_locked), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

internal fun ConsumptionMode.labelRes() = when (this) {
    ConsumptionMode.DETAILED -> R.string.fund_mode_detailed
    ConsumptionMode.DAILY_ESTIMATE -> R.string.fund_mode_daily_estimate
    ConsumptionMode.NONE -> R.string.fund_mode_none
}

private val previewState = FundEditorUiState(
    loaded = true,
    name = "Pasajes",
    mode = ConsumptionMode.DAILY_ESTIMATE,
    estimateText = "5.00",
    startsOn = LocalDate.of(2026, 10, 1),
    endsOn = LocalDate.of(2026, 10, 31),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FundEditorContentPreview() {
    FundEditorContent(previewState, {})
}

@Preview(showBackground = true)
@Composable
private fun FundEditorLockedPreview() {
    FundEditorContent(previewState.copy(isEditing = true, modeLocked = true, startLocked = true), {})
}
