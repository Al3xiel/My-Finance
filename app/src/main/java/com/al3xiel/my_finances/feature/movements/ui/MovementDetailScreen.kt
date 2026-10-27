package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.core.designsystem.ReasonDialog
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.core.designsystem.message
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.movements.domain.model.HistoryKind
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementHistoryEntry
import com.al3xiel.my_finances.feature.movements.domain.model.MovementPart
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.Instant
import java.time.LocalDate

/** S21: datos, partes e historial de versiones; corregir abre el editor, anular pide un motivo. */
@Composable
fun MovementDetailScreen(
    movementId: MovementId,
    onCorrect: (MovementId, MovementType) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovementDetailViewModel = hiltViewModel<MovementDetailViewModel, MovementDetailViewModel.Factory>(
        key = movementId.value,
        creationCallback = { it.create(movementId.value) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MovementDetailContent(
        state = state,
        onAction = viewModel::onAction,
        onCorrect = { state.detail?.movement?.type?.let { onCorrect(movementId, it) } },
        modifier = modifier,
    )
}

@Composable
fun MovementDetailContent(
    state: MovementDetailUiState,
    onAction: (MovementDetailUiAction) -> Unit,
    onCorrect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    if (detail == null) {
        if (state.loaded) EmptyState(title = stringResource(R.string.movement_detail_not_found), body = "", modifier = modifier)
        return
    }
    val movement = detail.movement
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(movement.type.labelRes()), style = MaterialTheme.typography.titleLarge)
        MoneyText(movement.amount, style = MaterialTheme.typography.headlineMedium)
        if (movement.status == MovementStatus.VOIDED) {
            Text(stringResource(R.string.history_voided), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.movement_detail_revision, movement.revision), style = MaterialTheme.typography.bodySmall)
        Field(R.string.movement_detail_date, dayText(movement.occurredOn))
        val account = state.names.accounts[movement.accountId.value].orEmpty()
        val counter = movement.counterAccountId?.let { state.names.accounts[it.value].orEmpty() }
        Field(R.string.movement_detail_account, listOfNotNull(account, counter).joinToString(" → "))
        movement.incomeSourceId?.let { Field(R.string.sources_title, state.names.incomeSources[it.value].orEmpty()) }
        movement.note?.let { Field(R.string.movement_detail_note, it) }

        if (movement.parts.isNotEmpty()) {
            Section(R.string.movement_detail_parts)
            movement.parts.forEach { part -> PartRow(part, movement.amount.currency, state.names) }
        }

        Section(R.string.movement_detail_history)
        detail.history.forEach { entry -> HistoryRow(entry) }

        when {
            state.canEdit -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onCorrect) { Text(stringResource(R.string.movement_detail_correct)) }
                OutlinedButton(onClick = { onAction(MovementDetailUiAction.ShowVoidDialog) }) {
                    Text(stringResource(R.string.movement_detail_void))
                }
            }
            movement.type == MovementType.TRANSFER ->
                Text(stringResource(R.string.error_transfer_not_editable), style = MaterialTheme.typography.bodySmall)
        }
    }
    if (state.showVoidDialog) {
        ReasonDialog(
            title = stringResource(R.string.movement_detail_void_title),
            onConfirm = { onAction(MovementDetailUiAction.ConfirmVoid(it)) },
            onDismiss = { onAction(MovementDetailUiAction.DismissVoidDialog) },
        )
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(MovementDetailUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(MovementDetailUiAction.DismissWarning) },
        )
    }
    state.error?.let { NoticeDialog(it, onDismiss = { onAction(MovementDetailUiAction.DismissError) }) }
}

@Composable
private fun Field(labelRes: Int, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(labelRes), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

@Composable
private fun Section(titleRes: Int) {
    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun PartRow(part: MovementPart, currency: CurrencyCode, names: CatalogNames) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            val category = names.categories[part.categoryId.value].orEmpty()
            val title = if (part.kind == PartKind.FEE) stringResource(R.string.movement_detail_fee) else category
            Text(title, style = MaterialTheme.typography.bodyLarge)
            val sub = if (part.kind == PartKind.FEE) category else part.tagIds.joinToString(", ") { names.tags[it.value].orEmpty() }
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoneyText(Money(part.amount.amountMinor, currency))
    }
}

@Composable
private fun HistoryRow(entry: MovementHistoryEntry) {
    Column {
        val label = when (entry.kind) {
            HistoryKind.REGISTERED -> R.string.history_registered
            HistoryKind.CORRECTED -> R.string.history_corrected
            HistoryKind.VOIDED -> R.string.history_voided
        }
        Text(stringResource(R.string.history_entry, stringResource(label), dayText(entry.occurredOn)), style = MaterialTheme.typography.bodyMedium)
        entry.reason?.let {
            Text(stringResource(R.string.history_reason, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val previewMovement = Movement(
    id = MovementId("m1"),
    type = MovementType.EXPENSE,
    accountId = AccountId("a1"),
    counterAccountId = null,
    amount = Money(3_000, CurrencyCode.PEN),
    incomeSourceId = null,
    note = "Mercado",
    occurredOn = LocalDate.of(2026, 10, 25),
    status = MovementStatus.ACTIVE,
    revision = 2,
    parts = listOf(
        MovementPart(PartId("p1"), Money(2_000, CurrencyCode.PEN), CategoryId("c1"), PartKind.NORMAL, listOf(TagId("t1"))),
        MovementPart(PartId("p2"), Money(1_000, CurrencyCode.PEN), CategoryId("c2"), PartKind.NORMAL, emptyList()),
    ),
)

private val previewState = MovementDetailUiState(
    detail = MovementDetail(
        previewMovement,
        listOf(
            MovementHistoryEntry(1, HistoryKind.REGISTERED, LocalDate.of(2026, 10, 25), Instant.EPOCH, null),
            MovementHistoryEntry(2, HistoryKind.CORRECTED, LocalDate.of(2026, 10, 25), Instant.EPOCH, "monto mal"),
        ),
    ),
    loaded = true,
    names = CatalogNames(
        accounts = mapOf("a1" to "BBVA"),
        categories = mapOf("c1" to "Comida", "c2" to "Hogar"),
        tags = mapOf("t1" to "Viaje"),
    ),
)

@Preview(showBackground = true)
@Composable
private fun MovementDetailContentPreview() {
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { MovementDetailContent(previewState, {}, {}) } }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MovementDetailContentDarkPreview() {
    MyfinancesTheme { MovementDetailContent(previewState.copy(showVoidDialog = true), {}, {}) }
}
