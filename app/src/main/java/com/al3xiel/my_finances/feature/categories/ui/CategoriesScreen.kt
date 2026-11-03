package com.al3xiel.my_finances.feature.categories.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.CatalogScaffold
import com.al3xiel.my_finances.core.designsystem.CatalogRow
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
    CatalogScaffold(
        title = stringResource(R.string.categories_title),
        onAdd = { onAction(CategoriesUiAction.ShowCreate(parentId = null)) },
        isEmpty = state.nodes.isEmpty(),
        modifier = modifier,
    ) {
        state.nodes.forEach { node ->
            key(node.category.id.value) {
                SectionCard {
                    CatalogRow(
                        title = node.category.name,
                        onRename = { onAction(CategoriesUiAction.ShowRename(node.category.id, node.category.name)) },
                        onArchive = { onAction(CategoriesUiAction.Archive(node.category.id)) },
                    )
                    node.children.forEach { child ->
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        CatalogRow(
                            title = child.name,
                            onRename = { onAction(CategoriesUiAction.ShowRename(child.id, child.name)) },
                            onArchive = { onAction(CategoriesUiAction.Archive(child.id)) },
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                    PillButton(
                        text = stringResource(R.string.categories_add_child),
                        onClick = { onAction(CategoriesUiAction.ShowCreate(parentId = node.category.id)) },
                        style = PillStyle.TEXT,
                        modifier = Modifier.padding(start = 8.dp),
                    )
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
