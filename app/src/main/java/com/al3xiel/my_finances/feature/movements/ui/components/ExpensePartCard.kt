package com.al3xiel.my_finances.feature.movements.ui.components

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
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.feature.movements.ui.components.PartPoolPicker
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import java.time.LocalDate
import kotlin.math.abs
import com.al3xiel.my_finances.feature.movements.ui.ExpenseEditorUiAction
import com.al3xiel.my_finances.feature.movements.ui.ExpenseEditorUiState
import com.al3xiel.my_finances.feature.movements.ui.PartDraft

@Composable
fun ExpensePartCard(number: Int, part: PartDraft, state: ExpenseEditorUiState, onAction: (ExpenseEditorUiAction) -> Unit) {
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
            if (state.poolOptions.isNotEmpty()) {
                PartPoolPicker(
                    options = state.poolOptions,
                    selectedId = part.poolId,
                    onSelect = { onAction(ExpenseEditorUiAction.SelectPartPool(part.key, it)) },
                )
            }
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpensePartCardPreview() {
    val state = ExpenseEditorUiState(
        loaded = true,
        parts = listOf(PartDraft(0, "10", CategoryId("c1"), setOf(TagId("t1")), poolId = PoolId("dogs"))),
        categoryOptions = listOf(EntityOption("c1", "Mascotas")),
        tagOptions = listOf(EntityOption("t1", "Perros")),
        poolOptions = listOf(EntityOption("dogs", "Perros – mamá")),
    )
    ExpensePartCard(1, state.parts.single(), state, onAction = {})
}
