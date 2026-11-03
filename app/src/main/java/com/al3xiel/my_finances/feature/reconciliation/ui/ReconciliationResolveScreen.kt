package com.al3xiel.my_finances.feature.reconciliation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.AdjustmentSpec
import com.al3xiel.my_finances.feature.reconciliation.ui.components.DifferenceText
import com.al3xiel.my_finances.feature.reconciliation.ui.components.labelRes

/** S62: causa, vista del ajuste y nota. `onFinished` cierra la pantalla una vez resuelta. */
@Composable
fun ReconciliationResolveScreen(
    reconciliationId: ReconciliationId,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReconciliationResolveViewModel = hiltViewModel<ReconciliationResolveViewModel, ReconciliationResolveViewModel.Factory>(
        key = reconciliationId.value,
        creationCallback = { it.create(reconciliationId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.resolved) { if (state.resolved) onFinished() }
    ReconciliationResolveContent(state, viewModel::onAction, modifier)
}

@Composable
fun ReconciliationResolveContent(
    state: ReconciliationResolveUiState,
    onAction: (ReconciliationResolveUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val difference = state.difference
    if (difference == null) {
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            if (state.notFound) EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = Modifier.padding(padding))
        }
        return
    }
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.reconciliation_resolve_title), modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.sm)) {
                Text(stringResource(R.string.reconciliation_remaining), style = MaterialTheme.typography.titleMedium)
                DifferenceText(difference, style = MaterialTheme.typography.titleMedium)
            }
            SectionCard(title = stringResource(R.string.reconciliation_resolve_cause)) {
                state.causes.forEachIndexed { index, cause ->
                    if (index > 0) SectionDivider()
                    CauseOption(cause, selected = cause == state.selectedCause, onSelect = { onAction(ReconciliationResolveUiAction.SelectCause(cause)) })
                }
            }
            AdjustmentPreview(state.adjustment)
            OutlinedTextField(
                value = state.note,
                onValueChange = { onAction(ReconciliationResolveUiAction.EditNote(it)) },
                label = { Text(stringResource(R.string.reconciliation_resolve_note)) },
                minLines = 2,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
            PillButton(stringResource(R.string.reconciliation_resolve_confirm), { onAction(ReconciliationResolveUiAction.Confirm) }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CauseOption(cause: ReconciliationCause, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = LocalSpacing.current.lg),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(cause.labelRes()))
    }
}

@Composable
private fun AdjustmentPreview(adjustment: AdjustmentSpec?) {
    if (adjustment == null) {
        Text(stringResource(R.string.reconciliation_resolve_no_adjustment), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val label = when (adjustment.direction) {
        AdjustmentDirection.UNIDENTIFIED_INCOME -> R.string.reconciliation_resolve_adjustment_income
        AdjustmentDirection.UNIDENTIFIED_EXPENSE -> R.string.reconciliation_resolve_adjustment_expense
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        MoneyText(adjustment.amount, style = MaterialTheme.typography.titleMedium)
    }
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = ReconciliationResolveUiState(
    loaded = true,
    difference = pen(240),
    causes = listOf(ReconciliationCause.SUSPECTED_APP_ERROR, ReconciliationCause.UNKNOWN),
    selectedCause = ReconciliationCause.UNKNOWN,
    adjustment = AdjustmentSpec(AdjustmentDirection.UNIDENTIFIED_INCOME, pen(240)),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReconciliationResolveContentPreview() {
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { ReconciliationResolveContent(previewState, {}) } }
}

@Preview(showBackground = true)
@Composable
private fun ReconciliationResolveBalancedPreview() {
    ReconciliationResolveContent(
        ReconciliationResolveUiState(
            loaded = true,
            difference = pen(0),
            causes = listOf(ReconciliationCause.BALANCED, ReconciliationCause.MISSING_RECORD, ReconciliationCause.RECORDING_ERROR),
            selectedCause = ReconciliationCause.BALANCED,
        ),
        {},
    )
}
