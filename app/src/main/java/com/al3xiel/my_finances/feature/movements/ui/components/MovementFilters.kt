package com.al3xiel.my_finances.feature.movements.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.DateChooserDialog
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.dayText
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.movements.ui.FilterOptions
import com.al3xiel.my_finances.feature.movements.ui.MovementsUiAction
import com.al3xiel.my_finances.feature.movements.ui.MovementsUiState
import com.al3xiel.my_finances.feature.movements.ui.labelRes
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import androidx.compose.ui.tooling.preview.Preview

/** Filtros de S20: cuenta, tipo, categoría, concepto y rango de fechas. */
@Composable
internal fun FilterRow(state: MovementsUiState, onAction: (MovementsUiAction) -> Unit) {
    var pickingFrom by rememberSaveable { mutableStateOf(false) }
    var pickingTo by rememberSaveable { mutableStateOf(false) }
    val filter = state.filter
    val typeOptions = MovementType.entries.map { EntityOption(it.name, stringResource(it.labelRes())) }

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuChip(R.string.movements_filter_account, state.options.accounts, filter.accountId?.value) {
            onAction(MovementsUiAction.SetAccount(it?.let(::AccountId)))
        }
        MenuChip(R.string.movements_filter_type, typeOptions, filter.type?.name) {
            onAction(MovementsUiAction.SetType(it?.let(MovementType::valueOf)))
        }
        MenuChip(R.string.movements_filter_category, state.options.categories, filter.categoryId?.value) {
            onAction(MovementsUiAction.SetCategory(it?.let(::CategoryId)))
        }
        MenuChip(R.string.movements_filter_tag, state.options.tags, filter.tagId?.value) {
            onAction(MovementsUiAction.SetTag(it?.let(::TagId)))
        }
        FilterChip(
            selected = filter.from != null,
            onClick = { pickingFrom = true },
            label = { Text(filter.from?.let(::dayText) ?: stringResource(R.string.movements_filter_from)) },
        )
        FilterChip(
            selected = filter.to != null,
            onClick = { pickingTo = true },
            label = { Text(filter.to?.let(::dayText) ?: stringResource(R.string.movements_filter_to)) },
        )
        if (state.isFiltered) {
            TextButton(onClick = { onAction(MovementsUiAction.ClearFilters) }) {
                Text(stringResource(R.string.movements_filter_clear))
            }
        }
    }
    if (pickingFrom) {
        DateChooserDialog(
            onDateSelected = {
                pickingFrom = false
                onAction(MovementsUiAction.SetFrom(it))
            },
            onDismiss = { pickingFrom = false },
            initialDate = filter.from,
        )
    }
    if (pickingTo) {
        DateChooserDialog(
            onDateSelected = {
                pickingTo = false
                onAction(MovementsUiAction.SetTo(it))
            },
            onDismiss = { pickingTo = false },
            initialDate = filter.to,
        )
    }
}

/** Chip que abre un menú de opciones; "Todos" quita el filtro. */
@Composable
private fun MenuChip(labelRes: Int, options: List<EntityOption>, selectedId: String?, onSelect: (String?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selectedId != null,
            onClick = { open = true },
            label = { Text(options.firstOrNull { it.id == selectedId }?.label ?: stringResource(labelRes)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.movements_filter_any)) },
                onClick = {
                    open = false
                    onSelect(null)
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        open = false
                        onSelect(option.id)
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FilterRowPreview() {
    MyfinancesTheme {
        FilterRow(
            state = MovementsUiState(
                options = FilterOptions(accounts = listOf(EntityOption("a", "BBVA"))),
                filter = com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter(type = MovementType.EXPENSE),
                isFiltered = true,
            ),
            onAction = {},
        )
    }
}
