package com.al3xiel.my_finances.feature.quickentry.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import com.al3xiel.my_finances.core.designsystem.EntityChips
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.StatusBadge
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.MoneyInput
import com.al3xiel.my_finances.feature.quickentry.domain.model.Ambiguity
import com.al3xiel.my_finances.feature.quickentry.domain.model.Candidate
import com.al3xiel.my_finances.feature.quickentry.domain.model.Conflict
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import com.al3xiel.my_finances.feature.quickentry.ui.QuickEntryUiAction
import com.al3xiel.my_finances.feature.quickentry.ui.QuickEntryUiState
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** Lo entendido de la línea, siempre visible y editable. Lo sugerido y lo ambiguo se dicen con texto, no solo con color. */
@Composable
fun EntryPreviewCard(
    state: QuickEntryUiState,
    onAction: (QuickEntryUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val suggested = stringResource(R.string.quick_entry_suggested)
    val pickOne = stringResource(R.string.quick_entry_pick_one)
    SectionCard(
        title = stringResource(if (state.isIncome) R.string.quick_entry_preview_income else R.string.quick_entry_preview_expense),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.ambiguities.forEach { AmbiguityRow(it, onAction) }
            state.conflicts.forEach {
                Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error)
            }
            MoneyInput(
                value = state.amountText,
                onValueChange = { onAction(QuickEntryUiAction.EditAmount(it)) },
                label = stringResource(R.string.editor_amount),
                modifier = Modifier.fillMaxWidth(),
                large = true,
            )
            ChipsWithHint(
                label = stringResource(R.string.editor_account),
                options = state.accountOptions,
                selectedId = state.accountId,
                onSelect = { onAction(QuickEntryUiAction.SelectAccount(it)) },
                hint = hint(state.accountSuggested, Conflict.ACCOUNTS in state.conflicts, suggested, pickOne),
                isError = Conflict.ACCOUNTS in state.conflicts,
            )
            if (state.isIncome) {
                ChipsWithHint(
                    label = stringResource(R.string.income_source),
                    options = state.sourceOptions,
                    selectedId = state.sourceId,
                    onSelect = { onAction(QuickEntryUiAction.SelectSource(it)) },
                    hint = if (state.sourceId == null || Conflict.SOURCES in state.conflicts) pickOne else null,
                    isError = state.sourceId == null,
                )
            } else {
                ChipsWithHint(
                    label = stringResource(R.string.expense_part_category),
                    options = state.categoryOptions,
                    selectedId = state.categoryId,
                    onSelect = { onAction(QuickEntryUiAction.SelectCategory(it)) },
                    hint = hint(state.categorySuggested, state.categoryId == null || Conflict.CATEGORIES in state.conflicts, suggested, pickOne),
                    isError = state.categoryId == null,
                )
                if (state.tagNames.isNotEmpty()) {
                    Text(stringResource(R.string.quick_entry_tags, state.tagNames.joinToString()), style = MaterialTheme.typography.bodyMedium)
                }
                EntityPicker(
                    label = stringResource(R.string.expense_part_pool),
                    options = state.poolOptions,
                    selectedId = state.poolId,
                    onSelect = { onAction(QuickEntryUiAction.SelectPool(it)) },
                    isError = state.conflicts.any { it == Conflict.POOLS || it == Conflict.POOL_AND_FUND },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.poolId != null) {
                    TextButton(onClick = { onAction(QuickEntryUiAction.ClearPool) }) { Text(stringResource(R.string.quick_entry_clear)) }
                }
                EntityPicker(
                    label = stringResource(R.string.expense_part_fund),
                    options = state.fundOptions,
                    selectedId = state.fundId,
                    onSelect = { onAction(QuickEntryUiAction.SelectFund(it)) },
                    isError = state.conflicts.any { it == Conflict.FUNDS || it == Conflict.POOL_AND_FUND },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.fundId != null) {
                    TextButton(onClick = { onAction(QuickEntryUiAction.ClearFund) }) { Text(stringResource(R.string.quick_entry_clear)) }
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = { onAction(QuickEntryUiAction.EditNote(it)) },
                label = { Text(stringResource(R.string.editor_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmbiguityRow(ambiguity: Ambiguity, onAction: (QuickEntryUiAction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatusBadge(BadgeStatus.NEEDS_REVIEW)
        Text(
            stringResource(R.string.quick_entry_ambiguous, ambiguity.text),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ambiguity.candidates.forEach { candidate ->
                AssistChip(
                    onClick = { onAction(QuickEntryUiAction.Choose(ambiguity, candidate)) },
                    label = { Text(stringResource(R.string.quick_entry_candidate, candidate.label, stringResource(candidate.kind.labelRes()))) },
                )
            }
        }
    }
}

/** Chips de una entidad con, debajo, el texto que dice por qué hay que mirarla (sugerida, a elegir); el color solo refuerza. */
@Composable
private fun ChipsWithHint(
    label: String,
    options: List<EntityOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    hint: String?,
    isError: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        EntityChips(label = label, options = options, selectedId = selectedId, onSelect = onSelect, modifier = Modifier.fillMaxWidth())
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun hint(isSuggested: Boolean, mustPick: Boolean, suggested: String, pickOne: String): String? = when {
    mustPick -> pickOne
    isSuggested -> suggested
    else -> null
}

private fun Conflict.messageRes(): Int = when (this) {
    Conflict.ACCOUNTS -> R.string.quick_entry_conflict_accounts
    Conflict.CATEGORIES -> R.string.quick_entry_conflict_categories
    Conflict.POOL_AND_FUND -> R.string.quick_entry_conflict_pool_fund
    Conflict.POOLS -> R.string.quick_entry_conflict_pools
    Conflict.FUNDS -> R.string.quick_entry_conflict_funds
    Conflict.SOURCES -> R.string.quick_entry_conflict_sources
}

private fun EntityKind.labelRes(): Int = when (this) {
    EntityKind.ACCOUNT -> R.string.quick_entry_kind_account
    EntityKind.CATEGORY -> R.string.quick_entry_kind_category
    EntityKind.TAG -> R.string.quick_entry_kind_tag
    EntityKind.POOL -> R.string.quick_entry_kind_pool
    EntityKind.FUND -> R.string.quick_entry_kind_fund
    EntityKind.INCOME_SOURCE -> R.string.quick_entry_kind_source
}

private val previewState = QuickEntryUiState(
    line = "45 com",
    amountText = "45",
    accountId = "a",
    accountSuggested = true,
    categoryId = null,
    accountOptions = listOf(EntityOption("a", "BBVA")),
    categoryOptions = listOf(EntityOption("c", "Comida")),
    ambiguities = listOf(
        Ambiguity("com", listOf(Candidate(EntityKind.CATEGORY, "c", "Comida"), Candidate(EntityKind.TAG, "t", "Comidas"))),
    ),
)

@Preview(showBackground = true)
@Composable
private fun EntryPreviewCardPreview() {
    MyfinancesTheme { EntryPreviewCard(previewState, {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EntryPreviewCardDarkPreview() {
    MyfinancesTheme { EntryPreviewCard(previewState, {}) }
}
