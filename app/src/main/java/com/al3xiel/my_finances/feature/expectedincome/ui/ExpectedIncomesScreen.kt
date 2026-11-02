package com.al3xiel.my_finances.feature.expectedincome.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S55: ingresos esperados con su estado (vencido, programado, confirmado, cancelado). */
@Composable
fun ExpectedIncomesScreen(
    onSchedule: () -> Unit,
    onEdit: (ExpectedIncomeId) -> Unit,
    onConfirm: (ExpectedIncomeId) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpectedIncomesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExpectedIncomesContent(state, viewModel::onAction, onSchedule, onEdit, onConfirm, modifier)
}

@Composable
fun ExpectedIncomesContent(
    state: ExpectedIncomesUiState,
    onAction: (ExpectedIncomesUiAction) -> Unit,
    onSchedule: () -> Unit,
    onEdit: (ExpectedIncomeId) -> Unit,
    onConfirm: (ExpectedIncomeId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.loaded && state.items.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.expected_incomes_empty_title),
            body = stringResource(R.string.expected_incomes_empty_body),
            actionLabel = stringResource(R.string.expected_incomes_schedule),
            onAction = onSchedule,
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.expected_incomes_schedule)) }
        }
        items(state.items, key = { it.income.id.value }) { item ->
            ExpectedIncomeCard(item, onAction, onEdit, onConfirm)
        }
    }
    if (state.cancelling != null) CancelDialog(onAction)
    state.error?.let { NoticeDialog(error = it, onDismiss = { onAction(ExpectedIncomesUiAction.DismissError) }) }
}

@Composable
private fun ExpectedIncomeCard(
    item: ExpectedIncomeItem,
    onAction: (ExpectedIncomesUiAction) -> Unit,
    onEdit: (ExpectedIncomeId) -> Unit,
    onConfirm: (ExpectedIncomeId) -> Unit,
) {
    val income = item.income
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(item.sourceName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                MoneyText(income.estimated, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(
                    if (item.accountArchived) R.string.expected_income_account_archived else R.string.expected_income_account,
                    item.accountName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.expected_income_range, dayText(income.range.from), dayText(income.range.to)) +
                    if (income.repeatsMonthly) stringResource(R.string.expected_income_monthly_suffix) else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(income.status.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                color = if (income.status == ExpectedIncomeStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            income.cancelReason?.let {
                Text(stringResource(R.string.expected_income_cancel_reason, it), style = MaterialTheme.typography.bodySmall)
            }
            if (item.isPending) {
                if (item.accountArchived) {
                    Text(
                        stringResource(R.string.expected_income_edit_before_confirm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onConfirm(income.id) }, enabled = item.canConfirm) {
                        Text(stringResource(R.string.expected_income_confirm))
                    }
                    TextButton(onClick = { onEdit(income.id) }) { Text(stringResource(R.string.expected_income_edit)) }
                    TextButton(onClick = { onAction(ExpectedIncomesUiAction.AskCancel(income.id)) }) {
                        Text(stringResource(R.string.expected_income_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun CancelDialog(onAction: (ExpectedIncomesUiAction) -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onAction(ExpectedIncomesUiAction.DismissCancel) },
        title = { Text(stringResource(R.string.expected_income_cancel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.expected_income_cancel_body))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.expected_income_cancel_reason_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(ExpectedIncomesUiAction.ConfirmCancel(reason)) }) {
                Text(stringResource(R.string.expected_income_cancel))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(ExpectedIncomesUiAction.DismissCancel) }) {
                Text(stringResource(R.string.reason_dialog_cancel))
            }
        },
    )
}

internal fun ExpectedIncomeStatus.labelRes() = when (this) {
    ExpectedIncomeStatus.SCHEDULED -> R.string.expected_status_scheduled
    ExpectedIncomeStatus.OVERDUE -> R.string.expected_status_overdue
    ExpectedIncomeStatus.CONFIRMED -> R.string.expected_status_confirmed
    ExpectedIncomeStatus.CANCELLED -> R.string.expected_status_cancelled
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private fun previewItem(id: String, status: ExpectedIncomeStatus, archived: Boolean = false) = ExpectedIncomeItem(
    ExpectedIncome(
        ExpectedIncomeId(id), IncomeSourceId("s"), AccountId("a"), pen(200_000),
        ExpectedRange(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 11, 5)), true, status, null, null, null,
    ),
    accountName = "BBVA", sourceName = "Sueldo", accountArchived = archived,
)

private val previewState = ExpectedIncomesUiState(
    loaded = true,
    items = listOf(
        previewItem("1", ExpectedIncomeStatus.OVERDUE),
        previewItem("2", ExpectedIncomeStatus.SCHEDULED, archived = true),
        previewItem("3", ExpectedIncomeStatus.CONFIRMED),
    ),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpectedIncomesContentPreview() {
    MyfinancesTheme { ExpectedIncomesContent(previewState, onAction = {}, onSchedule = {}, onEdit = {}, onConfirm = {}) }
}
