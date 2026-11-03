package com.al3xiel.my_finances.feature.tags.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.CatalogRow
import com.al3xiel.my_finances.core.designsystem.EmptyState
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.NameDialog
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S57: conceptos agrupados por tipo (persona / tema). */
@Composable
fun TagsScreen(modifier: Modifier = Modifier, viewModel: TagsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TagsContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun TagsContent(state: TagsUiState, onAction: (TagsUiAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.tags_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onAction(TagsUiAction.ShowCreate) }) { Text(stringResource(R.string.catalog_add)) }
        }
        if (state.tags.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.catalog_empty_title),
                body = stringResource(R.string.catalog_empty_body),
            )
        } else {
            LazyColumn {
                section(R.string.tags_section_person, state.tags.filter { it.kind == TagKind.PERSON }, onAction)
                section(R.string.tags_section_topic, state.tags.filter { it.kind == TagKind.TOPIC }, onAction)
            }
        }
    }
    state.notice?.let { NoticeDialog(it, onDismiss = { onAction(TagsUiAction.DismissNotice) }) }
    state.dialog?.let { TagDialog(it, state.people, onAction) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titleRes: Int,
    tags: List<Tag>,
    onAction: (TagsUiAction) -> Unit,
) {
    if (tags.isEmpty()) return
    item(key = "header-$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(tags, key = { it.id.value }) { tag ->
        CatalogRow(
            title = tag.name,
            onRename = { onAction(TagsUiAction.ShowRename(tag.id, tag.name)) },
            onArchive = { onAction(TagsUiAction.Archive(tag.id)) },
        )
    }
}

@Composable
private fun TagDialog(dialog: TagDialogState, people: List<Person>, onAction: (TagsUiAction) -> Unit) {
    NameDialog(
        title = stringResource(if (dialog.renamingId != null) R.string.tags_rename else R.string.tags_new),
        name = dialog.name,
        onNameChange = { onAction(TagsUiAction.EditName(it)) },
        onConfirm = { onAction(TagsUiAction.Submit) },
        onDismiss = { onAction(TagsUiAction.DismissDialog) },
        error = dialog.error,
    ) {
        if (dialog.renamingId == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = dialog.kind == TagKind.TOPIC,
                    onClick = { onAction(TagsUiAction.SelectKind(TagKind.TOPIC)) },
                    label = { Text(stringResource(R.string.tags_kind_topic)) },
                )
                FilterChip(
                    selected = dialog.kind == TagKind.PERSON,
                    onClick = { onAction(TagsUiAction.SelectKind(TagKind.PERSON)) },
                    label = { Text(stringResource(R.string.tags_kind_person)) },
                )
            }
            if (dialog.kind == TagKind.PERSON) {
                EntityPicker(
                    label = stringResource(R.string.tags_person_label),
                    options = people.map { EntityOption(it.id.value, it.alias) },
                    selectedId = dialog.personId?.value,
                    onSelect = { onAction(TagsUiAction.SelectPerson(PersonId(it))) },
                )
            }
        }
    }
}

private val previewState = TagsUiState(
    tags = listOf(
        Tag(TagId("1"), "Mamá", TagKind.PERSON, PersonId("p"), false),
        Tag(TagId("2"), "Viaje", TagKind.TOPIC, null, false),
    ),
    people = listOf(Person(PersonId("p"), "Mamá", false)),
)

@Preview(showBackground = true)
@Composable
private fun TagsContentPreview() {
    MyfinancesTheme { TagsContent(previewState, onAction = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TagsContentDarkPreview() {
    MyfinancesTheme { TagsContent(previewState.copy(dialog = TagDialogState(kind = TagKind.PERSON)), onAction = {}) }
}
