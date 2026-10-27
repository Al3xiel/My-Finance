package com.al3xiel.my_finances.feature.pools.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.attempt
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.pools.domain.usecase.CreatePoolUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.RenamePoolUseCase
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PoolEditorUiState(
    val loaded: Boolean = false,
    val isEditing: Boolean = false,
    val notFound: Boolean = false,
    val name: String = "",
    val personId: PersonId? = null,
    val kind: PoolKind = PoolKind.PERMANENT,
    val categoryId: CategoryId? = null,
    val tagIds: Set<TagId> = emptySet(),
    val personOptions: List<EntityOption> = emptyList(),
    val categoryOptions: List<EntityOption> = emptyList(),
    val tagOptions: List<EntityOption> = emptyList(),
    val error: BusinessError? = null,
    val saved: Boolean = false,
)

sealed interface PoolEditorUiAction {
    data class EditName(val name: String) : PoolEditorUiAction
    data class SelectPerson(val id: PersonId) : PoolEditorUiAction
    data class SelectKind(val kind: PoolKind) : PoolEditorUiAction
    data class SelectCategory(val id: CategoryId?) : PoolEditorUiAction
    data class ToggleTag(val id: TagId) : PoolEditorUiAction
    data object Save : PoolEditorUiAction
}

/** S32: crea un cúmulo (responsable obligatorio) o, si ya existe, solo lo renombra: responsable y tipo no cambian. */
@HiltViewModel(assistedFactory = PoolEditorViewModel.Factory::class)
class PoolEditorViewModel @AssistedInject constructor(
    @Assisted poolIdValue: String?,
    private val pools: PoolRepository,
    people: PersonRepository,
    categories: CategoryRepository,
    tags: TagRepository,
    private val createPool: CreatePoolUseCase,
    private val renamePool: RenamePoolUseCase,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(poolIdValue: String?): PoolEditorViewModel
    }

    private val poolId = poolIdValue?.let(::PoolId)

    private data class Form(
        val loaded: Boolean = false,
        val notFound: Boolean = false,
        val name: String = "",
        val personId: PersonId? = null,
        val kind: PoolKind = PoolKind.PERMANENT,
        val categoryId: CategoryId? = null,
        val tagIds: Set<TagId> = emptySet(),
        val error: BusinessError? = null,
        val saved: Boolean = false,
        val submitting: Boolean = false,
    )

    private val form = MutableStateFlow(Form())

    val uiState: StateFlow<PoolEditorUiState> = combine(
        form, people.observeAll(), categories.observeAll(), tags.observeAll(),
    ) { form, peopleList, categoryList, tagList ->
        PoolEditorUiState(
            loaded = form.loaded,
            isEditing = poolId != null,
            notFound = form.notFound,
            name = form.name,
            personId = form.personId,
            kind = form.kind,
            categoryId = form.categoryId,
            tagIds = form.tagIds,
            personOptions = peopleList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.alias) },
            categoryOptions = categoryList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.name) },
            tagOptions = tagList.filter { !it.isArchived }.map { EntityOption(it.id.value, it.name) },
            error = form.error,
            saved = form.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PoolEditorUiState())

    init {
        viewModelScope.launch {
            val id = poolId
            if (id == null) {
                form.update { it.copy(loaded = true) }
                return@launch
            }
            val pool = pools.find(id)
            form.update {
                if (pool == null) {
                    it.copy(loaded = true, notFound = true)
                } else {
                    it.copy(
                        loaded = true,
                        name = pool.name,
                        personId = pool.responsiblePersonId,
                        kind = pool.kind,
                        categoryId = pool.defaultCategoryId,
                        tagIds = pool.defaultTagIds.toSet(),
                    )
                }
            }
        }
    }

    fun onAction(action: PoolEditorUiAction) {
        when (action) {
            is PoolEditorUiAction.EditName -> edit { copy(name = action.name) }
            is PoolEditorUiAction.SelectPerson -> edit { copy(personId = action.id) }
            is PoolEditorUiAction.SelectKind -> edit { copy(kind = action.kind) }
            is PoolEditorUiAction.SelectCategory -> edit { copy(categoryId = action.id) }
            is PoolEditorUiAction.ToggleTag -> edit { copy(tagIds = if (action.id in tagIds) tagIds - action.id else tagIds + action.id) }
            PoolEditorUiAction.Save -> save()
        }
    }

    private fun edit(change: Form.() -> Form) = form.update { it.change().copy(error = null) }

    private fun save() {
        val current = form.value
        if (current.submitting) return
        form.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val id = poolId
            val error = if (id != null) {
                attempt { renamePool(id, current.name) }.errorOrNull()
            } else {
                attempt { createPool(current.name, current.personId, current.kind, current.categoryId, current.tagIds.toList()) }.errorOrNull()
            }
            form.update { if (error == null) it.copy(saved = true, submitting = false) else it.copy(error = error, submitting = false) }
        }
    }
}
