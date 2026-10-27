package com.al3xiel.my_finances.feature.loans.ui

import android.content.res.Configuration
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
import com.al3xiel.my_finances.core.designsystem.AccountAmountFields
import com.al3xiel.my_finances.core.designsystem.DateField
import com.al3xiel.my_finances.core.designsystem.EditorFooter
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.PersonId
import java.time.LocalDate

/** S36 (nuevo): presta dinero a una persona; sale de la cuenta pero no es un gasto. */
@Composable
fun LoanEditorScreen(
    personId: PersonId?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoanEditorViewModel = hiltViewModel<LoanEditorViewModel, LoanEditorViewModel.Factory>(
        key = personId?.value ?: "new",
        creationCallback = { it.create(personId?.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    LoanEditorContent(state, viewModel::onAction, modifier)
}

@Composable
fun LoanEditorContent(
    state: LoanEditorUiState,
    onAction: (LoanEditorUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.loan_editor_title), style = MaterialTheme.typography.titleLarge)
        EntityPicker(
            label = stringResource(R.string.loan_editor_person),
            options = state.personOptions,
            selectedId = state.personId?.value,
            onSelect = { onAction(LoanEditorUiAction.SelectPerson(PersonId(it))) },
            modifier = Modifier.fillMaxWidth(),
        )
        AccountAmountFields(
            accountLabel = stringResource(R.string.loan_editor_account),
            accountOptions = state.accountOptions,
            accountId = state.accountId,
            onAccountSelect = { onAction(LoanEditorUiAction.SelectAccount(it)) },
            amountText = state.amountText,
            onAmountChange = { onAction(LoanEditorUiAction.EditAmount(it)) },
        )
        DateField(
            label = stringResource(R.string.editor_date),
            value = state.occurredOn,
            onValueChange = { onAction(LoanEditorUiAction.SelectDate(it)) },
            maxDate = state.today,
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = { onAction(LoanEditorUiAction.EditNote(it)) },
            label = { Text(stringResource(R.string.editor_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        EditorFooter(error = state.error, onSave = { onAction(LoanEditorUiAction.Save) })
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(LoanEditorUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(LoanEditorUiAction.DismissWarning) },
        )
    }
}

private val previewState = LoanEditorUiState(
    loaded = true,
    personId = PersonId("pepe"),
    accountId = AccountId("bbva"),
    amountText = "50",
    occurredOn = LocalDate.of(2026, 11, 4),
    today = LocalDate.of(2026, 11, 4),
    personOptions = listOf(EntityOption("pepe", "Pepe")),
    accountOptions = listOf(EntityOption("bbva", "BBVA")),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoanEditorContentPreview() {
    LoanEditorContent(previewState, {})
}
