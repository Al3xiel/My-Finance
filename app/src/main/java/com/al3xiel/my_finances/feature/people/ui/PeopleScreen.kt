package com.al3xiel.my_finances.feature.people.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.al3xiel.my_finances.core.designsystem.NameDialog
import com.al3xiel.my_finances.core.designsystem.NoticeDialog
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S58: personas (alias). */
@Composable
fun PeopleScreen(modifier: Modifier = Modifier, viewModel: PeopleViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PeopleContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun PeopleContent(state: PeopleUiState, onAction: (PeopleUiAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.people_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onAction(PeopleUiAction.ShowCreate) }) { Text(stringResource(R.string.catalog_add)) }
        }
        if (state.people.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.catalog_empty_title),
                body = stringResource(R.string.catalog_empty_body),
            )
        } else {
            LazyColumn {
                items(state.people, key = { it.id.value }) { person ->
                    CatalogRow(
                        title = person.alias,
                        onRename = { onAction(PeopleUiAction.ShowRename(person.id, person.alias)) },
                        onArchive = { onAction(PeopleUiAction.Archive(person.id)) },
                    )
                }
            }
        }
    }
    state.notice?.let { NoticeDialog(it, onDismiss = { onAction(PeopleUiAction.DismissNotice) }) }
    state.dialog?.let { dialog ->
        NameDialog(
            title = stringResource(if (dialog.renamingId != null) R.string.people_rename else R.string.people_new),
            name = dialog.name,
            onNameChange = { onAction(PeopleUiAction.EditName(it)) },
            onConfirm = { onAction(PeopleUiAction.Submit) },
            onDismiss = { onAction(PeopleUiAction.DismissDialog) },
            label = stringResource(R.string.people_alias),
            error = dialog.error,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PeopleContentPreview() {
    MyfinancesTheme {
        PeopleContent(PeopleUiState(listOf(Person(PersonId("1"), "Mamá", false), Person(PersonId("2"), "Papá", false))), onAction = {})
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleContentDarkPreview() {
    MyfinancesTheme { PeopleContent(PeopleUiState(), onAction = {}) }
}
