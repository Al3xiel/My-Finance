package com.al3xiel.my_finances.feature.categories.ui

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
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** S56: categorías de un solo nivel. */
@Composable
fun CategoriesScreen(modifier: Modifier = Modifier, viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoriesContent(state = state, onAction = viewModel::onAction, modifier = modifier)
}

@Composable
fun CategoriesContent(
    state: CategoriesUiState,
    onAction: (CategoriesUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.categories_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onAction(CategoriesUiAction.ShowCreate(parentId = null)) }) {
                Text(stringResource(R.string.catalog_add))
            }
        }
        if (state.nodes.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.catalog_empty_title),
                body = stringResource(R.string.catalog_empty_body),
            )
        } else {
            LazyColumn {
                items(state.nodes, key = { it.category.id.value }) { node ->
                    CatalogRow(
                        title = node.category.name,
                        onRename = { onAction(CategoriesUiAction.ShowRename(node.category.id, node.category.name)) },
                        onArchive = { onAction(CategoriesUiAction.Archive(node.category.id)) },
                    )
                    node.children.forEach { child ->
                        CatalogRow(
                            title = child.name,
                            onRename = { onAction(CategoriesUiAction.ShowRename(child.id, child.name)) },
                            onArchive = { onAction(CategoriesUiAction.Archive(child.id)) },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                    TextButton(
                        onClick = { onAction(CategoriesUiAction.ShowCreate(parentId = node.category.id)) },
                        modifier = Modifier.padding(start = 16.dp),
                    ) { Text(stringResource(R.string.categories_add_child)) }
                }
            }
        }
    }
    state.notice?.let { NoticeDialog(it, onDismiss = { onAction(CategoriesUiAction.DismissNotice) }) }
    state.dialog?.let { dialog ->
        NameDialog(
            title = stringResource(
                when {
                    dialog.renamingId != null -> R.string.categories_rename
                    dialog.parentId != null -> R.string.categories_new_child
                    else -> R.string.categories_new
                },
            ),
            name = dialog.name,
            onNameChange = { onAction(CategoriesUiAction.EditName(it)) },
            onConfirm = { onAction(CategoriesUiAction.Submit) },
            onDismiss = { onAction(CategoriesUiAction.DismissDialog) },
            error = dialog.error,
        )
    }
}

private val previewState = CategoriesUiState(
    nodes = listOf(
        CategoryNode(
            Category(CategoryId("1"), "Comida", null, false),
            listOf(Category(CategoryId("2"), "Restaurantes", CategoryId("1"), false)),
        ),
        CategoryNode(Category(CategoryId("3"), "Hogar", null, false), emptyList()),
    ),
)

@Preview(showBackground = true)
@Composable
private fun CategoriesContentPreview() {
    MyfinancesTheme { CategoriesContent(previewState, onAction = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoriesContentDarkPreview() {
    MyfinancesTheme { CategoriesContent(CategoriesUiState(), onAction = {}) }
}
