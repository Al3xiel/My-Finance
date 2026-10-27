package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EditorFooter
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.core.designsystem.ReasonDialog
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S23: registrar un ingreso o, si llega un id, corregirlo. Al guardar vuelve a la pantalla anterior. */
@Composable
fun IncomeEditorScreen(
    movementId: MovementId?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IncomeEditorViewModel = hiltViewModel<IncomeEditorViewModel, IncomeEditorViewModel.Factory>(
        key = "income-${movementId?.value}",
        creationCallback = { it.create(movementId?.value) },
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
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isCorrection) R.string.income_correct_title else R.string.income_new_title),
            style = MaterialTheme.typography.titleLarge,
        )
        MoneyInput(
            value = state.amountText,
            onValueChange = { onAction(IncomeEditorUiAction.EditAmount(it)) },
            label = stringResource(R.string.editor_amount),
            modifier = Modifier.fillMaxWidth(),
        )
        EntityPicker(
            label = stringResource(R.string.editor_account),
            options = state.accountOptions,
            selectedId = state.accountId?.value,
            onSelect = { onAction(IncomeEditorUiAction.SelectAccount(AccountId(it))) },
            modifier = Modifier.fillMaxWidth(),
        )
        EntityPicker(
            label = stringResource(R.string.income_source),
            options = state.sourceOptions,
            selectedId = state.sourceId?.value,
            onSelect = { onAction(IncomeEditorUiAction.SelectSource(IncomeSourceId(it))) },
            modifier = Modifier.fillMaxWidth(),
        )
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
        EditorFooter(error = state.error, onSave = { onAction(IncomeEditorUiAction.Save) })
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
