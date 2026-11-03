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
import com.al3xiel.my_finances.core.designsystem.EditorLayout
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
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
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = Modifier.padding(padding))
        }
        return
    }
    val title = stringResource(if (state.isEditing) R.string.fund_editor_title_edit else R.string.fund_editor_title_new)
    ScreenScaffold(title = title, modifier = modifier) { padding ->
        EditorLayout(error = state.error, onSave = { onAction(FundEditorUiAction.Save) }, modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onAction(FundEditorUiAction.EditName(it)) },
                label = { Text(stringResource(R.string.fund_editor_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
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
                PillButton(stringResource(R.string.fund_editor_ends_on), { onAction(FundEditorUiAction.SetEndsOn(state.startsOn)) }, style = PillStyle.TEXT)
            } else {
                DateField(stringResource(R.string.fund_editor_ends_on), endsOn, { onAction(FundEditorUiAction.SetEndsOn(it)) })
                PillButton(stringResource(R.string.fund_editor_no_end), { onAction(FundEditorUiAction.SetEndsOn(null)) }, style = PillStyle.TEXT)
            }
        }
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
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun FundEditorContentPreview() {
    MyfinancesTheme { FundEditorContent(previewState, {}) }
}

@Preview(showBackground = true)
@Composable
private fun FundEditorLockedPreview() {
    FundEditorContent(previewState.copy(isEditing = true, modeLocked = true, startLocked = true), {})
}
