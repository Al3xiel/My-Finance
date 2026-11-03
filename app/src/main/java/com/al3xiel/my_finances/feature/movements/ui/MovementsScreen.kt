package com.al3xiel.my_finances.feature.movements.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.al3xiel.my_finances.core.designsystem.AppIcons
import com.al3xiel.my_finances.core.designsystem.MoneyText
import com.al3xiel.my_finances.core.designsystem.NegativeBalanceDialog
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.core.designsystem.ReasonDialog
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.designsystem.MovementRow
import com.al3xiel.my_finances.core.designsystem.MovementRowKind
import com.al3xiel.my_finances.core.designsystem.dayHeaderText
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.feature.movements.ui.components.FilterRow
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate

/** S20: movimientos agrupados por día, con búsqueda en notas y filtros; tocar uno abre su detalle (S21). */
@Composable
fun MovementsScreen(
    onOpenMovement: (MovementId) -> Unit,
    modifier: Modifier = Modifier,
    /** Abre el editor del tipo del movimiento (atajo de deslizar; también se puede desde el detalle). */
    onCorrect: (MovementId, MovementType) -> Unit = { _, _ -> },
    preset: MovementsPreset = MovementsPreset(),
    viewModel: MovementsViewModel = hiltViewModel<MovementsViewModel, MovementsViewModel.Factory>(
        key = preset.toString(),
        creationCallback = { it.create(preset) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MovementsContent(
        state = state,
        onAction = viewModel::onAction,
        onOpenMovement = onOpenMovement,
        onCorrect = onCorrect,
        modifier = modifier,
    )
}

@Composable
fun MovementsContent(
    state: MovementsUiState,
    onAction: (MovementsUiAction) -> Unit,
    onOpenMovement: (MovementId) -> Unit,
    modifier: Modifier = Modifier,
    onCorrect: (MovementId, MovementType) -> Unit = { _, _ -> },
) {
    ScreenScaffold(title = stringResource(R.string.movements_title), showBack = false, modifier = modifier) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchText,
                onValueChange = { onAction(MovementsUiAction.SetText(it)) },
                label = { Text(stringResource(R.string.movements_search)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            FilterRow(state, onAction)
            when {
                !state.loaded -> Unit
                state.days.isEmpty() -> EmptyState(
                    title = stringResource(R.string.movements_empty_title),
                    body = stringResource(
                        if (state.isFiltered) R.string.movements_empty_filtered_body else R.string.movements_empty_body,
                    ),
                )
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    state.days.forEach { section ->
                        item(key = "day-${section.day}") { DayHeader(section) }
                        items(section.rows.size, key = { "row-${section.rows[it].id.value}" }) { index ->
                            SwipeableMovementRow(
                                row = section.rows[index],
                                onOpen = onOpenMovement,
                                onCorrect = onCorrect,
                                onAskVoid = { onAction(MovementsUiAction.AskVoid(it)) },
                            )
                        }
                    }
                }
            }
        }
    }
    state.voidingId?.let {
        ReasonDialog(
            title = stringResource(R.string.movement_detail_void_title),
            onConfirm = { reason -> onAction(MovementsUiAction.ConfirmVoid(reason)) },
            onDismiss = { onAction(MovementsUiAction.DismissVoid) },
        )
    }
    state.negativeWarning?.let {
        NegativeBalanceDialog(
            warning = it,
            onConfirm = { onAction(MovementsUiAction.ConfirmNegativeBalance) },
            onDismiss = { onAction(MovementsUiAction.DismissWarning) },
        )
    }
    state.error?.let { NoticeDialog(it, onDismiss = { onAction(MovementsUiAction.DismissError) }) }
}

/** Fecha del día y, a la derecha, su subtotal (ingresos menos gastos); la cifra respeta «ocultar montos». */
@Composable
private fun DayHeader(section: DaySection) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayHeaderText(section.day),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        MoneyText(section.subtotal, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Deslizar es solo un atajo (la acción también está en el detalle): hacia el final anula, hacia el inicio corrige.
 * La fila siempre vuelve a su sitio; quien confirma es el diálogo o el editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMovementRow(
    row: MovementRowUi,
    onOpen: (MovementId) -> Unit,
    onCorrect: (MovementId, MovementType) -> Unit,
    onAskVoid: (MovementId) -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    // Una sola vez cuando el deslizamiento se asienta (confirmValueChange se repite durante el arrastre); la fila vuelve a su sitio.
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> if (row.canCorrect) onCorrect(row.id, row.type)
            SwipeToDismissBoxValue.EndToStart -> if (row.canVoid) onAskVoid(row.id)
            SwipeToDismissBoxValue.Settled -> Unit
        }
        if (state.currentValue != SwipeToDismissBoxValue.Settled) state.reset()
    }
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = row.canCorrect,
        enableDismissFromEndToStart = row.canVoid,
        backgroundContent = { SwipeBackground(state.dismissDirection) },
    ) {
        MovementRow(
            title = row.note ?: stringResource(row.type.labelRes()),
            subtitle = listOfNotNull(row.accountName, row.counterAccountName).joinToString(" → "),
            amount = row.amount,
            kind = row.type.rowKind(),
            isVoided = row.isVoided,
            onClick = { onOpen(row.id) },
            icon = row.type.icon(),
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val correct = direction == SwipeToDismissBoxValue.StartToEnd
    val label = stringResource(if (correct) R.string.movement_detail_correct else R.string.movement_detail_void)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (correct) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = if (correct) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (direction != SwipeToDismissBoxValue.Settled) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (correct) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

internal fun MovementType.labelRes() = when (this) {
    MovementType.INCOME -> R.string.movement_type_income
    MovementType.EXPENSE -> R.string.movement_type_expense
    MovementType.TRANSFER -> R.string.movement_type_transfer
    MovementType.REIMBURSEMENT -> R.string.movement_type_reimbursement
    MovementType.LOAN_DISBURSEMENT -> R.string.movement_type_loan_disbursement
    MovementType.LOAN_REPAYMENT -> R.string.movement_type_loan_repayment
    MovementType.FUND_UNTRACKED_SPEND -> R.string.movement_type_fund_untracked_spend
    MovementType.ADJUSTMENT -> R.string.movement_type_adjustment
}

internal fun MovementType.rowKind() = when (this) {
    MovementType.INCOME -> MovementRowKind.INCOME
    MovementType.EXPENSE -> MovementRowKind.EXPENSE
    MovementType.TRANSFER, MovementType.ADJUSTMENT -> MovementRowKind.TRANSFER
    MovementType.REIMBURSEMENT, MovementType.LOAN_REPAYMENT -> MovementRowKind.INCOME
    MovementType.LOAN_DISBURSEMENT, MovementType.FUND_UNTRACKED_SPEND -> MovementRowKind.EXPENSE
}

/** Icono de la fila según el tipo (la fila no trae la categoría, y la lista no hace consultas nuevas). */
@DrawableRes
internal fun MovementType.icon(): Int = when (rowKind()) {
    MovementRowKind.INCOME -> AppIcons.Add
    MovementRowKind.EXPENSE -> AppIcons.ActionExpense
    MovementRowKind.TRANSFER -> AppIcons.ActionTransfer
}

private fun row(id: String, type: MovementType, minor: Long, note: String?, counter: String? = null, voided: Boolean = false) =
    MovementRowUi(MovementId(id), type, "BBVA", counter, note, Money(minor, CurrencyCode.PEN), voided, canCorrect = !voided, canVoid = !voided)

private val previewState = MovementsUiState(
    days = listOf(
        DaySection(
            LocalDate.of(2026, 10, 26),
            listOf(
                row("1", MovementType.EXPENSE, 3_000, "Mercado"),
                row("2", MovementType.TRANSFER, 100_000, null, counter = "Efectivo"),
            ),
            Money(-3_000, CurrencyCode.PEN),
        ),
        DaySection(LocalDate.of(2026, 10, 24), listOf(row("3", MovementType.INCOME, 500_000, "Sueldo"), row("4", MovementType.EXPENSE, 1_000, null, voided = true)), Money(500_000, CurrencyCode.PEN)),
    ),
    options = FilterOptions(accounts = listOf(EntityOption("a", "BBVA"))),
    loaded = true,
)

@Preview(showBackground = true)
@Composable
private fun MovementsContentPreview() {
    MyfinancesTheme { CompositionLocalProvider(LocalHideAmounts provides false) { MovementsContent(previewState, {}, {}) } }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MovementsContentDarkPreview() {
    MyfinancesTheme { MovementsContent(MovementsUiState(loaded = true), {}, {}) }
}
