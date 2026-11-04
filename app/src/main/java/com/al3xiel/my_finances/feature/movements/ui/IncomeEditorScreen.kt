package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
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
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.core.designsystem.DateField
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.FundPicker
import com.al3xiel.my_finances.core.designsystem.EditorLayout
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityChips
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.core.designsystem.ReasonDialog
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S23: registrar un ingreso o, si llega un id, corregirlo. Al guardar vuelve a la pantalla anterior. */
@Composable
fun IncomeEditorScreen(
    movementId: MovementId?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    expectedIncomeId: String? = null,
    /** Si se abre desde una conciliación, el ingreso queda vinculado a ella. */
    reconciliationId: ReconciliationId? = null,
    viewModel: IncomeEditorViewModel = hiltViewModel<IncomeEditorViewModel, IncomeEditorViewModel.Factory>(
        key = "income-${movementId?.value}-${expectedIncomeId.orEmpty()}-${reconciliationId?.value.orEmpty()}",
        creationCallback = { it.create(movementId?.value, expectedIncomeId, reconciliationId?.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    IncomeEditorContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun IncomeEditorContent(
    state: IncomeEditorUiState,
    onAction: (IncomeEditorUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.notFound) {
        EmptyState(title = stringResource(R.string.editor_not_found), body = "", modifier = modifier)
        return
    }
    EditorLayout(error = state.error, onSave = { onAction(IncomeEditorUiAction.Save) }, saving = state.saving, modifier = modifier) {
        Text(
            stringResource(if (state.isCorrection) R.string.income_correct_title else R.string.income_new_title),
            style = MaterialTheme.typography.titleLarge,
        )
        MoneyInput(
            value = state.amountText,
            onValueChange = { onAction(IncomeEditorUiAction.EditAmount(it)) },
            label = stringResource(R.string.editor_amount),
            modifier = Modifier.fillMaxWidth(),
            large = true,
        )
        EntityChips(
            label = stringResource(R.string.editor_account),
            options = state.accountOptions,
            selectedId = state.accountId?.value,
            onSelect = { onAction(IncomeEditorUiAction.SelectAccount(AccountId(it))) },
            modifier = Modifier.fillMaxWidth(),
        )
        EntityChips(
            label = stringResource(R.string.income_source),
            options = state.sourceOptions,
            selectedId = state.sourceId?.value,
            onSelect = { onAction(IncomeEditorUiAction.SelectSource(IncomeSourceId(it))) },
            modifier = Modifier.fillMaxWidth(),
            emptyHint = stringResource(R.string.hint_no_income_sources),
        )
        state.selectedExpected?.let { selected ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.income_expected_confirming, selected.sourceName, MoneyFormatter.format(selected.estimated)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onAction(IncomeEditorUiAction.ClearExpected) }) {
                        Text(stringResource(R.string.income_expected_clear))
                    }
                }
            }
        }
        state.suggestions.forEach { suggestion ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.income_expected_suggestion, suggestion.sourceName, MoneyFormatter.format(suggestion.estimated)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { onAction(IncomeEditorUiAction.SelectExpected(suggestion.id)) }) {
                        Text(stringResource(R.string.income_expected_confirm))
                    }
                }
            }
        }
        DateField(
            label = stringResource(R.string.editor_date),
            value = state.occurredOn,
            onValueChange = { onAction(IncomeEditorUiAction.SelectOccurredOn(it)) },
            maxDate = state.today,
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = { onAction(IncomeEditorUiAction.EditNote(it)) },
            label = { Text(stringResource(R.string.editor_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.fundOptions.isNotEmpty()) {
            FundPicker(
                label = stringResource(R.string.income_set_aside),
                options = state.fundOptions,
                selectedId = state.topUpFundId,
                onSelect = { onAction(IncomeEditorUiAction.SelectTopUpFund(it)) },
            )
            if (state.topUpFundId != null) {
                MoneyInput(
                    value = state.topUpText,
                    onValueChange = { onAction(IncomeEditorUiAction.EditTopUp(it)) },
                    label = stringResource(R.string.income_set_aside_amount),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (state.showReasonDialog) {
        ReasonDialog(
            title = stringResource(R.string.income_correct_title),
            onConfirm = { onAction(IncomeEditorUiAction.ConfirmReason(it)) },
            onDismiss = { onAction(IncomeEditorUiAction.DismissReason) },
        )
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(IncomeEditorUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(IncomeEditorUiAction.DismissWarning) },
        )
    }
}

private val previewState = IncomeEditorUiState(
    loaded = true,
    amountText = "1500",
    accountId = AccountId("a"),
    sourceId = IncomeSourceId("s"),
    occurredOn = LocalDate.of(2026, 10, 26),
    today = LocalDate.of(2026, 10, 26),
    accountOptions = listOf(EntityOption("a", "BBVA")),
    sourceOptions = listOf(EntityOption("s", "Sueldo")),
)

@Preview(showBackground = true)
@Composable
private fun IncomeEditorContentPreview() {
    MyfinancesTheme { IncomeEditorContent(previewState, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomeEditorContentDarkPreview() {
    MyfinancesTheme { IncomeEditorContent(previewState.copy(isCorrection = true, showReasonDialog = true), {}) }
}
