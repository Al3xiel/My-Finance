package com.al3xiel.my_finances.feature.categories.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.categories.domain.usecase.ArchiveCategoryUseCase
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.categories.domain.usecase.RenameCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryNode(val category: Category, val children: List<Category>)

/** `renamingId == null` = alta; `parentId` solo cuenta en el alta. */
data class CategoryDialogState(
    val parentId: CategoryId? = null,
    val renamingId: CategoryId? = null,
    val name: String = "",
    val error: BusinessError? = null,
)

data class CategoriesUiState(
    val nodes: List<CategoryNode> = emptyList(),
    val dialog: CategoryDialogState? = null,
    /** Rechazo de una acción sin diálogo (archivar). */
    val notice: BusinessError? = null,
)

sealed interface CategoriesUiAction {
    data class ShowCreate(val parentId: CategoryId?) : CategoriesUiAction
    data class ShowRename(val id: CategoryId, val currentName: String) : CategoriesUiAction
    data class EditName(val name: String) : CategoriesUiAction
    data object Submit : CategoriesUiAction
    data object DismissDialog : CategoriesUiAction
    data class Archive(val id: CategoryId) : CategoriesUiAction
    data object DismissNotice : CategoriesUiAction
}

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    categories: CategoryRepository,
    private val createCategory: CreateCategoryUseCase,
    private val renameCategory: RenameCategoryUseCase,
    private val archiveCategory: ArchiveCategoryUseCase,
) : ViewModel() {
    private val dialog = MutableStateFlow<CategoryDialogState?>(null)
    private val notice = MutableStateFlow<BusinessError?>(null)

    val uiState: StateFlow<CategoriesUiState> = combine(categories.observeAll(), dialog, notice) { all, dialog, notice ->
        CategoriesUiState(nodes = tree(all), dialog = dialog, notice = notice)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CategoriesUiState())

    fun onAction(action: CategoriesUiAction) {
        when (action) {
            is CategoriesUiAction.ShowCreate -> dialog.value = CategoryDialogState(parentId = action.parentId)
            is CategoriesUiAction.ShowRename ->
                dialog.value = CategoryDialogState(renamingId = action.id, name = action.currentName)
            is CategoriesUiAction.EditName -> dialog.update { it?.copy(name = action.name, error = null) }
            CategoriesUiAction.DismissDialog -> dialog.value = null
            CategoriesUiAction.Submit -> submit()
            is CategoriesUiAction.Archive -> viewModelScope.launch { notice.value = archiveCategory(action.id).errorOrNull() }
            CategoriesUiAction.DismissNotice -> notice.value = null
        }
    }

    private fun submit() {
        val current = dialog.value ?: return
        viewModelScope.launch {
            val outcome = if (current.renamingId != null) {
                renameCategory(current.renamingId, current.name)
            } else {
                createCategory(current.name, DEFAULT_ICON, DEFAULT_COLOR, current.parentId)
            }
            val error = outcome.errorOrNull()
            dialog.update { if (error == null) null else it?.copy(error = error) }
        }
    }

    private fun tree(all: List<Category>): List<CategoryNode> {
        val active = all.filter { !it.isArchived }
        return active.filter { it.parentId == null }.map { root ->
            CategoryNode(root, active.filter { it.parentId == root.id })
        }
    }

    private companion object {
        const val DEFAULT_ICON = "category"
        const val DEFAULT_COLOR = "#A1887F"
    }
}
