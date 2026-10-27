package com.al3xiel.my_finances.feature.accounts.ui

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
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.core.designsystem.EntityPicker
import com.al3xiel.my_finances.core.designsystem.NameDialog
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S59: fuentes de ingreso; solo se crean (las del sistema no se tocan). */
@Composable
fun IncomeSourcesScreen(modifier: Modifier = Modifier, viewModel: IncomeSourcesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    IncomeSourcesContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun IncomeSourcesContent(
    state: IncomeSourcesUiState,
    onAction: (IncomeSourcesUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.sources_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onAction(IncomeSourcesUiAction.ShowCreate) }) {
                Text(stringResource(R.string.catalog_add))
            }
        }
        if (state.sources.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.catalog_empty_title),
                body = stringResource(R.string.catalog_empty_body),
            )
        } else {
            LazyColumn {
                items(state.sources, key = { it.id.value }) { source ->
                    CatalogRow(
                        title = source.name,
                        subtitle = stringResource(R.string.sources_system).takeIf { source.kind != IncomeSourceKind.REGULAR },
                        onRename = {},
                        onArchive = {},
                        canEdit = false,
                    )
                }
            }
        }
    }
    state.dialog?.let { dialog ->
        NameDialog(
            title = stringResource(R.string.sources_new),
            name = dialog.name,
            onNameChange = { onAction(IncomeSourcesUiAction.EditName(it)) },
            onConfirm = { onAction(IncomeSourcesUiAction.Submit) },
            onDismiss = { onAction(IncomeSourcesUiAction.DismissDialog) },
            error = dialog.error,
        ) {
            EntityPicker(
                label = stringResource(R.string.sources_person_label),
                options = state.people.map { EntityOption(it.id.value, it.alias) },
                selectedId = dialog.personId?.value,
                onSelect = { onAction(IncomeSourcesUiAction.SelectPerson(PersonId(it))) },
            )
        }
    }
}

private val previewState = IncomeSourcesUiState(
    sources = listOf(
        IncomeSource(IncomeSourceId("1"), "Reembolso", IncomeSourceKind.REIMBURSEMENT, null),
        IncomeSource(IncomeSourceId("2"), "Sueldo", IncomeSourceKind.REGULAR, null),
    ),
    people = listOf(Person(PersonId("p"), "Mamá", false)),
)

@Preview(showBackground = true)
@Composable
private fun IncomeSourcesContentPreview() {
    MyfinancesTheme { IncomeSourcesContent(previewState, onAction = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomeSourcesContentDarkPreview() {
    MyfinancesTheme { IncomeSourcesContent(IncomeSourcesUiState(), onAction = {}) }
}
