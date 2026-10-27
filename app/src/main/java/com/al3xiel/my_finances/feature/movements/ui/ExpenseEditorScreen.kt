package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EditorFooter
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.core.designsystem.ReasonDialog
import com.al3xiel.my_finances.core.designsystem.moneyDisplayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate
import kotlin.math.abs

/** S22: registrar un gasto o, si llega un id, corregirlo. Al guardar vuelve a la pantalla anterior. */
@Composable
fun ExpenseEditorScreen(
    movementId: MovementId?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpenseEditorViewModel = hiltViewModel<ExpenseEditorViewModel, ExpenseEditorViewModel.Factory>(
        key = "expense-${movementId?.value}",
        creationCallback = { it.create(movementId?.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    ExpenseEditorContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun ExpenseEditorContent(
    state: ExpenseEditorUiState,
    onAction: (ExpenseEditorUiAction) -> Unit,
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
            stringResource(if (state.isCorrection) R.string.expense_correct_title else R.string.expense_new_title),
            style = MaterialTheme.typography.titleLarge,
        )
        MoneyInput(
            value = state.totalText,
            onValueChange = { onAction(ExpenseEditorUiAction.EditTotal(it)) },
            label = stringResource(R.string.editor_total),
            modifier = Modifier.fillMaxWidth(),
        )
        EntityPicker(
            label = stringResource(R.string.editor_account),
            options = state.accountOptions,
            selectedId = state.accountId?.value,
            onSelect = { onAction(ExpenseEditorUiAction.SelectAccount(AccountId(it))) },
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            label = stringResource(R.string.editor_date),
            value = state.occurredOn,
            onValueChange = { onAction(ExpenseEditorUiAction.SelectOccurredOn(it)) },
            maxDate = state.today,
        )
        OutlinedTextField(
            value = state.note,
            onValueChange = { onAction(ExpenseEditorUiAction.EditNote(it)) },
            label = { Text(stringResource(R.string.editor_note)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.expense_parts), style = MaterialTheme.typography.titleMedium)
        state.parts.forEachIndexed { index, part ->
            PartCard(index + 1, part, state, onAction)
        }
        if (state.parts.size > 1) UnassignedIndicator(state.unassignedMinor)
        OutlinedButton(onClick = { onAction(ExpenseEditorUiAction.AddPart) }) { Text(stringResource(R.string.expense_add_part)) }

        EditorFooter(error = state.error, onSave = { onAction(ExpenseEditorUiAction.Save) })
    }
    if (state.showReasonDialog) {
        ReasonDialog(
            title = stringResource(R.string.expense_correct_title),
            onConfirm = { onAction(ExpenseEditorUiAction.ConfirmReason(it)) },
            onDismiss = { onAction(ExpenseEditorUiAction.DismissReason) },
        )
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(ExpenseEditorUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(ExpenseEditorUiAction.DismissWarning) },
        )
    }
}

@Composable
private fun PartCard(number: Int, part: PartDraft, state: ExpenseEditorUiState, onAction: (ExpenseEditorUiAction) -> Unit) {
    val several = state.parts.size > 1
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.expense_part_title, number), style = MaterialTheme.typography.titleSmall)
                if (several) {
                    TextButton(onClick = { onAction(ExpenseEditorUiAction.RemovePart(part.key)) }) {
                        Text(stringResource(R.string.expense_remove_part))
                    }
                }
            }
            if (several) {
                MoneyInput(
                    value = part.amountText,
                    onValueChange = { onAction(ExpenseEditorUiAction.EditPartAmount(part.key, it)) },
                    label = stringResource(R.string.expense_part_amount),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EntityPicker(
                label = stringResource(R.string.expense_part_category),
                options = state.categoryOptions,
                selectedId = part.categoryId?.value,
                onSelect = { onAction(ExpenseEditorUiAction.SelectPartCategory(part.key, CategoryId(it))) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.tagOptions.isNotEmpty()) {
                Text(stringResource(R.string.expense_part_tags), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tagOptions.forEach { tag ->
                        FilterChip(
                            selected = TagId(tag.id) in part.tagIds,
                            onClick = { onAction(ExpenseEditorUiAction.TogglePartTag(part.key, TagId(tag.id))) },
                            label = { Text(tag.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnassignedIndicator(unassignedMinor: Long) {
    val amount = moneyDisplayText(Money(abs(unassignedMinor), CurrencyCode.PEN), hidden = LocalHideAmounts.current)
    when {
        unassignedMinor > 0 -> Text(stringResource(R.string.expense_unassigned, amount), color = MaterialTheme.colorScheme.error)
        unassignedMinor < 0 -> Text(stringResource(R.string.expense_over_assigned, amount), color = MaterialTheme.colorScheme.error)
        else -> Text(stringResource(R.string.expense_fully_assigned), color = MaterialTheme.colorScheme.primary)
    }
}

private val previewState = ExpenseEditorUiState(
    loaded = true,
    totalText = "10",
    accountId = AccountId("a"),
    occurredOn = LocalDate.of(2026, 10, 26),
    today = LocalDate.of(2026, 10, 26),
    parts = listOf(
        PartDraft(0, "6", CategoryId("c1"), setOf(TagId("t1"))),
        PartDraft(1, "3", CategoryId("c2")),
    ),
    accountOptions = listOf(EntityOption("a", "BBVA")),
    categoryOptions = listOf(EntityOption("c1", "Comida"), EntityOption("c2", "Hogar")),
    tagOptions = listOf(EntityOption("t1", "Viaje")),
    unassignedMinor = 100,
)

@Preview(showBackground = true)
@Composable
private fun ExpenseEditorContentPreview() {
    MyfinancesTheme { ExpenseEditorContent(previewState, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpenseEditorContentDarkPreview() {
    MyfinancesTheme { ExpenseEditorContent(previewState.copy(isCorrection = true, parts = previewState.parts.take(1), unassignedMinor = 0), {}) }
}
