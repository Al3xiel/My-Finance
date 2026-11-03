package com.al3xiel.my_finances.feature.pools.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
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
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.feature.pools.domain.model.PendingPartLine
import com.al3xiel.my_finances.feature.pools.ui.components.PendingPartsSection
import java.time.LocalDate

/** S34: reembolso recibido, con las partes que saldaría y el bloqueo si excede lo pendiente (D1). */
@Composable
fun ReimbursementEditorScreen(
    personId: PersonId?,
    poolId: PoolId?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReimbursementEditorViewModel = hiltViewModel<ReimbursementEditorViewModel, ReimbursementEditorViewModel.Factory>(
        key = "${personId?.value}/${poolId?.value}",
        creationCallback = { it.create(personId?.value, poolId?.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    ReimbursementEditorContent(state, viewModel::onAction, personLocked = personId != null, modifier = modifier)
}

@Composable
fun ReimbursementEditorContent(
    state: ReimbursementEditorUiState,
    onAction: (ReimbursementEditorUiAction) -> Unit,
    personLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.reimbursement_title), style = MaterialTheme.typography.titleLarge)
        if (!personLocked) {
            EntityPicker(
                label = stringResource(R.string.reimbursement_person),
                options = state.personOptions,
                selectedId = state.personId?.value,
                onSelect = { onAction(ReimbursementEditorUiAction.SelectPerson(PersonId(it))) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            state.personOptions.firstOrNull { it.id == state.personId?.value }?.let {
                Text(it.label, style = MaterialTheme.typography.titleMedium)
            }
        }
        AccountAmountFields(
            accountLabel = stringResource(R.string.reimbursement_account),
            accountOptions = state.accountOptions,
            accountId = state.accountId,
            onAccountSelect = { onAction(ReimbursementEditorUiAction.SelectAccount(it)) },
            amountText = state.amountText,
            onAmountChange = { onAction(ReimbursementEditorUiAction.EditAmount(it)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AllocationStrategy.entries.forEach { strategy ->
                FilterChip(
                    selected = state.strategy == strategy,
                    onClick = { onAction(ReimbursementEditorUiAction.SelectStrategy(strategy)) },
                    label = { Text(stringResource(strategy.labelRes())) },
                )
            }
        }
        DateField(
            label = stringResource(R.string.editor_date),
            value = state.occurredOn,
            onValueChange = { onAction(ReimbursementEditorUiAction.SelectDate(it)) },
            maxDate = state.today,
        )
        PendingPartsSection(state, onAction)
        OutlinedTextField(
            value = state.note,
            onValueChange = { onAction(ReimbursementEditorUiAction.EditNote(it)) },
            label = { Text(stringResource(R.string.editor_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        EditorFooter(error = state.error, onSave = { onAction(ReimbursementEditorUiAction.Save) })
    }
}

private fun AllocationStrategy.labelRes() = when (this) {
    AllocationStrategy.OLDEST_FIRST -> R.string.reimbursement_strategy_oldest
    AllocationStrategy.MANUAL -> R.string.reimbursement_strategy_manual
}

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

private val previewState = ReimbursementEditorUiState(
    loaded = true,
    personId = PersonId("mom"),
    personOptions = listOf(EntityOption("mom", "Mamá")),
    accountOptions = listOf(EntityOption("bbva", "BBVA")),
    accountId = AccountId("bbva"),
    amountText = "150",
    occurredOn = LocalDate.of(2026, 11, 3),
    today = LocalDate.of(2026, 11, 3),
    lines = listOf(
        ReimbursementLineUi(PendingPartLine(PartId("p1"), PoolId("dogs"), "Perros – mamá", LocalDate.of(2026, 10, 3), 4, "Mascotas", listOf("Perros"), pen(12_000)), pen(12_000), ""),
        ReimbursementLineUi(PendingPartLine(PartId("p2"), PoolId("dogs"), "Perros – mamá", LocalDate.of(2026, 10, 12), 9, "Comida", emptyList(), pen(8_000)), pen(3_000), ""),
    ),
    pendingTotal = pen(20_000),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReimbursementEditorContentPreview() {
    ReimbursementEditorContent(previewState, {}, personLocked = true)
}

@Preview(showBackground = true)
@Composable
private fun ReimbursementEditorManualPreview() {
    ReimbursementEditorContent(previewState.copy(strategy = AllocationStrategy.MANUAL, unassigned = pen(5_000), exceedsPending = true), {}, personLocked = false)
}
