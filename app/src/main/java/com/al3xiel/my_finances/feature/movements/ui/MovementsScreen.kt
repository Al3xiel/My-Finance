package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    viewModel: MovementsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MovementsContent(state = state, onAction = viewModel::onAction, onOpenMovement = onOpenMovement, modifier = modifier)
}

@Composable
fun MovementsContent(
    state: MovementsUiState,
    onAction: (MovementsUiAction) -> Unit,
    onOpenMovement: (MovementId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.movements_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
        )
        OutlinedTextField(
            value = state.searchText,
            onValueChange = { onAction(MovementsUiAction.SetText(it)) },
            label = { Text(stringResource(R.string.movements_search)) },
            singleLine = true,
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
            else -> LazyColumn {
                state.days.forEach { section ->
                    item(key = "day-${section.day}") {
                        Text(
                            dayHeaderText(section.day),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(section.rows.size, key = { "row-${section.rows[it].id.value}" }) { index ->
                        val row = section.rows[index]
                        MovementRow(
                            title = row.note ?: stringResource(row.type.labelRes()),
                            subtitle = listOfNotNull(row.accountName, row.counterAccountName).joinToString(" → "),
                            amount = row.amount,
                            kind = row.type.rowKind(),
                            isVoided = row.isVoided,
                            onClick = { onOpenMovement(row.id) },
                        )
                    }
                }
            }
        }
    }
}

internal fun MovementType.labelRes() = when (this) {
    MovementType.INCOME -> R.string.movement_type_income
    MovementType.EXPENSE -> R.string.movement_type_expense
    MovementType.TRANSFER -> R.string.movement_type_transfer
}

internal fun MovementType.rowKind() = when (this) {
    MovementType.INCOME -> MovementRowKind.INCOME
    MovementType.EXPENSE -> MovementRowKind.EXPENSE
    MovementType.TRANSFER -> MovementRowKind.TRANSFER
}

private fun row(id: String, type: MovementType, minor: Long, note: String?, counter: String? = null, voided: Boolean = false) =
    MovementRowUi(MovementId(id), type, "BBVA", counter, note, Money(minor, CurrencyCode.PEN), voided)

private val previewState = MovementsUiState(
    days = listOf(
        DaySection(
            LocalDate.of(2026, 10, 26),
            listOf(
                row("1", MovementType.EXPENSE, 3_000, "Mercado"),
                row("2", MovementType.TRANSFER, 100_000, null, counter = "Efectivo"),
            ),
        ),
        DaySection(LocalDate.of(2026, 10, 24), listOf(row("3", MovementType.INCOME, 500_000, "Sueldo"), row("4", MovementType.EXPENSE, 1_000, null, voided = true))),
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
