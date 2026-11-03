package com.al3xiel.my_finances.feature.pools.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EditorFooter
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.PoolKind

private const val NO_CATEGORY = ""

/** S32: crear un cúmulo (con responsable) o renombrar uno existente. */
@Composable
fun PoolEditorScreen(
    poolId: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PoolEditorViewModel = hiltViewModel<PoolEditorViewModel, PoolEditorViewModel.Factory>(
        key = poolId ?: "new",
        creationCallback = { it.create(poolId) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onFinished() }
    PoolEditorContent(state, viewModel::onAction, modifier)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoolEditorContent(
    state: PoolEditorUiState,
    onAction: (PoolEditorUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.notFound) {
        EmptyState(title = stringResource(R.string.error_not_found), body = "", modifier = modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isEditing) R.string.pool_editor_title_edit else R.string.pool_editor_title_new),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            value = state.name,
            onValueChange = { onAction(PoolEditorUiAction.EditName(it)) },
            label = { Text(stringResource(R.string.pool_editor_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.isEditing) {
            // Responsable y tipo no cambian: solo se renombra (no hay evento para cambiarlos).
            Text(stringResource(R.string.pool_editor_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            EntityPicker(
                label = stringResource(R.string.pool_editor_responsible),
                options = state.personOptions,
                selectedId = state.personId?.value,
                onSelect = { onAction(PoolEditorUiAction.SelectPerson(PersonId(it))) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = stringResource(R.string.pool_editor_responsible_hint),
            )
            Text(stringResource(R.string.pool_editor_kind), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PoolKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { onAction(PoolEditorUiAction.SelectKind(kind)) },
                        label = { Text(stringResource(kind.labelRes())) },
                    )
                }
            }
            EntityPicker(
                label = stringResource(R.string.pool_editor_category),
                options = listOf(EntityOption(NO_CATEGORY, stringResource(R.string.pool_editor_no_category))) + state.categoryOptions,
                selectedId = state.categoryId?.value ?: NO_CATEGORY,
                onSelect = { onAction(PoolEditorUiAction.SelectCategory(it.takeIf { id -> id != NO_CATEGORY }?.let(::CategoryId))) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.tagOptions.isNotEmpty()) {
                Text(stringResource(R.string.pool_editor_tags), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.tagOptions.forEach { option ->
                        val id = TagId(option.id)
                        FilterChip(
                            selected = id in state.tagIds,
                            onClick = { onAction(PoolEditorUiAction.ToggleTag(id)) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        }
        EditorFooter(error = state.error, onSave = { onAction(PoolEditorUiAction.Save) })
    }
}

private val previewState = PoolEditorUiState(
    loaded = true,
    name = "Perros – mamá",
    personId = PersonId("mom"),
    personOptions = listOf(EntityOption("mom", "Mamá")),
    categoryOptions = listOf(EntityOption("pets", "Mascotas")),
    tagOptions = listOf(EntityOption("dog", "Perros"), EntityOption("vet", "Veterinario")),
    tagIds = setOf(TagId("dog")),
)

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PoolEditorContentPreview() {
    PoolEditorContent(previewState, {})
}

@Preview(showBackground = true)
@Composable
private fun PoolEditorEditingPreview() {
    PoolEditorContent(previewState.copy(isEditing = true), {})
}
