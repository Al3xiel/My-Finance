package com.al3xiel.my_finances.feature.people.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.CatalogScaffold
import com.al3xiel.my_finances.core.designsystem.CatalogCard
import com.al3xiel.my_finances.core.designsystem.CatalogRow
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
    CatalogScaffold(
        title = stringResource(R.string.people_title),
        onAdd = { onAction(PeopleUiAction.ShowCreate) },
        isEmpty = state.people.isEmpty(),
        modifier = modifier,
    ) {
        CatalogCard(state.people) { person ->
            CatalogRow(
                title = person.alias,
                onRename = { onAction(PeopleUiAction.ShowRename(person.id, person.alias)) },
                onArchive = { onAction(PeopleUiAction.Archive(person.id)) },
            )
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
