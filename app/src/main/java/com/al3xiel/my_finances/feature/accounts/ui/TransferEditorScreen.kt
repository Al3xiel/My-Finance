package com.al3xiel.my_finances.feature.accounts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityChips
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S24: transferencia entre cuentas propias; solo se crea, no se edita (D12). */
@Composable
fun TransferEditorScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /** Si se abre desde una conciliación, la transferencia queda vinculada a ella. */
    reconciliationId: ReconciliationId? = null,
    viewModel: TransferEditorViewModel = hiltViewModel<TransferEditorViewModel, TransferEditorViewModel.Factory>(
        key = "transfer-${reconciliationId?.value.orEmpty()}",
        creationCallback = { it.create(reconciliationId?.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    TransferEditorContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun TransferEditorContent(
    state: TransferEditorUiState,
    onAction: (TransferEditorUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorLayout(error = state.error, onSave = { onAction(TransferEditorUiAction.Save) }, saving = state.saving, modifier = modifier) {
        Text(stringResource(R.string.transfer_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(state.label.labelRes()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        EntityChips(
            label = stringResource(R.string.transfer_from),
            options = state.accountOptions,
            selectedId = state.fromId?.value,
            onSelect = { onAction(TransferEditorUiAction.SelectFrom(AccountId(it))) },
            modifier = Modifier.fillMaxWidth(),
        )
        EntityChips(
            label = stringResource(R.string.transfer_to),
            options = state.accountOptions,
            selectedId = state.toId?.value,
            onSelect = { onAction(TransferEditorUiAction.SelectTo(AccountId(it))) },
            modifier = Modifier.fillMaxWidth(),
        )
        MoneyInput(
            value = state.amountText,
            onValueChange = { onAction(TransferEditorUiAction.EditAmount(it)) },
            label = stringResource(R.string.editor_amount),
            modifier = Modifier.fillMaxWidth(),
            large = true,
        )
        MoneyInput(
            value = state.feeText,
            onValueChange = { onAction(TransferEditorUiAction.EditFee(it)) },
            label = stringResource(R.string.transfer_fee),
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = stringResource(R.string.editor_date),
            value = state.occurredOn,
            onValueChange = { onAction(TransferEditorUiAction.SelectOccurredOn(it)) },
            maxDate = state.today,
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = { onAction(TransferEditorUiAction.EditNote(it)) },
            label = { Text(stringResource(R.string.editor_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(TransferEditorUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(TransferEditorUiAction.DismissWarning) },
        )
    }
}

private fun TransferLabel.labelRes() = when (this) {
    TransferLabel.WITHDRAWAL -> R.string.transfer_label_withdrawal
    TransferLabel.DEPOSIT -> R.string.transfer_label_deposit
    TransferLabel.TRANSFER -> R.string.transfer_label_transfer
}

private val previewState = TransferEditorUiState(
    loaded = true,
    fromId = AccountId("a"),
    toId = AccountId("b"),
    amountText = "100",
    feeText = "0.5",
    occurredOn = LocalDate.of(2026, 10, 26),
    today = LocalDate.of(2026, 10, 26),
    label = TransferLabel.WITHDRAWAL,
    accountOptions = listOf(EntityOption("a", "BBVA"), EntityOption("b", "Efectivo")),
)

@Preview(showBackground = true)
@Composable
private fun TransferEditorContentPreview() {
    MyfinancesTheme { TransferEditorContent(previewState, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TransferEditorContentDarkPreview() {
    MyfinancesTheme { TransferEditorContent(previewState.copy(label = TransferLabel.DEPOSIT), {}) }
}
