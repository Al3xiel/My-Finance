package com.al3xiel.my_finances.feature.movements.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityChips
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.FundPicker
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.feature.movements.ui.ExpenseEditorUiAction
import com.al3xiel.my_finances.feature.movements.ui.ExpenseEditorUiState
import com.al3xiel.my_finances.feature.movements.ui.PartDraft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensePartCard(number: Int, part: PartDraft, state: ExpenseEditorUiState, onAction: (ExpenseEditorUiAction) -> Unit) {
    val several = state.parts.size > 1
    SectionCard(title = stringResource(R.string.expense_part_title, number)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (several) {
                MoneyInput(
                    value = part.amountText,
                    onValueChange = { onAction(ExpenseEditorUiAction.EditPartAmount(part.key, it)) },
                    label = stringResource(R.string.expense_part_amount),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            EntityChips(
                label = stringResource(R.string.expense_part_category),
                options = state.categoryOptions,
                selectedId = part.categoryId?.value,
                onSelect = { onAction(ExpenseEditorUiAction.SelectPartCategory(part.key, CategoryId(it))) },
                modifier = Modifier.fillMaxWidth(),
                emptyHint = stringResource(R.string.hint_no_categories),
            )
            if (state.poolOptions.isNotEmpty()) {
                PartPoolPicker(
                    options = state.poolOptions,
                    selectedId = part.poolId,
                    onSelect = { onAction(ExpenseEditorUiAction.SelectPartPool(part.key, it)) },
                )
            }
            if (state.fundOptions.isNotEmpty()) {
                FundPicker(
                    label = stringResource(R.string.expense_part_fund),
                    options = state.fundOptions,
                    selectedId = part.fundId,
                    onSelect = { onAction(ExpenseEditorUiAction.SelectPartFund(part.key, it)) },
                )
            }
            if (state.tagOptions.isNotEmpty()) {
                Text(stringResource(R.string.expense_part_tags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.tagOptions.forEach { tag ->
                        FilterChip(
                            selected = TagId(tag.id) in part.tagIds,
                            onClick = { onAction(ExpenseEditorUiAction.TogglePartTag(part.key, TagId(tag.id))) },
                            label = { Text(tag.label) },
                        )
                    }
                }
            }
            if (several) {
                PillButton(
                    text = stringResource(R.string.expense_remove_part),
                    onClick = { onAction(ExpenseEditorUiAction.RemovePart(part.key)) },
                    style = PillStyle.TEXT,
                )
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
