package com.al3xiel.my_finances.feature.expectedincome.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.DateField
import com.al3xiel.my_finances.core.designsystem.EditorLayout
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** Programar un ingreso esperado o corregir uno pendiente. */
@Composable
fun ExpectedIncomeEditorScreen(
    expectedIncomeId: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpectedIncomeEditorViewModel = hiltViewModel<ExpectedIncomeEditorViewModel, ExpectedIncomeEditorViewModel.Factory>(
        key = "expected-income-${expectedIncomeId ?: "new"}",
        creationCallback = { it.create(expectedIncomeId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    ExpectedIncomeEditorContent(state, viewModel::onAction, modifier)
}

@Composable
fun ExpectedIncomeEditorContent(
    state: ExpectedIncomeEditorUiState,
    onAction: (ExpectedIncomeEditorUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.notFound || state.notEditable) {
        ScreenScaffold(title = "", modifier = modifier) { padding ->
            EmptyState(
                title = stringResource(if (state.notFound) R.string.error_not_found else R.string.error_not_pending),
                body = "",
                modifier = Modifier.padding(padding),
            )
        }
        return
    }
    val title = stringResource(if (state.isEditing) R.string.expected_income_editor_title_edit else R.string.expected_income_editor_title_new)
    ScreenScaffold(title = title, modifier = modifier) { padding ->
        EditorLayout(error = state.error, onSave = { onAction(ExpectedIncomeEditorUiAction.Save) }, modifier = Modifier.padding(padding)) {
            EntityPicker(
                label = stringResource(R.string.income_source),
                options = state.sourceOptions,
                selectedId = state.sourceId?.value,
                onSelect = { onAction(ExpectedIncomeEditorUiAction.SelectSource(IncomeSourceId(it))) },
                modifier = Modifier.fillMaxWidth(),
            )
            EntityPicker(
                label = stringResource(R.string.editor_account),
                options = state.accountOptions,
                selectedId = state.accountId?.value,
                onSelect = { onAction(ExpectedIncomeEditorUiAction.SelectAccount(AccountId(it))) },
                modifier = Modifier.fillMaxWidth(),
            )
            MoneyInput(
                value = state.amountText,
                onValueChange = { onAction(ExpectedIncomeEditorUiAction.EditAmount(it)) },
                label = stringResource(R.string.expected_income_editor_estimated),
                modifier = Modifier.fillMaxWidth(),
            )
            DateField(stringResource(R.string.expected_income_editor_from), state.expectedFrom, { onAction(ExpectedIncomeEditorUiAction.SetFrom(it)) })
            DateField(stringResource(R.string.expected_income_editor_to), state.expectedTo, { onAction(ExpectedIncomeEditorUiAction.SetTo(it)) })
            SectionCard {
                SectionRow(
                    headline = stringResource(R.string.expected_income_editor_repeats),
                    trailing = {
                        Switch(checked = state.repeatsMonthly, onCheckedChange = { onAction(ExpectedIncomeEditorUiAction.SetRepeatsMonthly(it)) })
                    },
                )
            }
        }
    }
}

private val previewState = ExpectedIncomeEditorUiState(
    loaded = true,
    amountText = "2000.00",
    accountId = AccountId("a"),
    sourceId = IncomeSourceId("s"),
    expectedFrom = LocalDate.of(2026, 10, 28),
    expectedTo = LocalDate.of(2026, 11, 5),
    repeatsMonthly = true,
    accountOptions = listOf(EntityOption("a", "BBVA")),
    sourceOptions = listOf(EntityOption("s", "Sueldo")),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ExpectedIncomeEditorContentPreview() {
    MyfinancesTheme { ExpectedIncomeEditorContent(previewState, onAction = {}) }
}
