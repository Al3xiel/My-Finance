package com.al3xiel.my_finances.feature.tags.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import com.al3xiel.my_finances.feature.tags.domain.usecase.ArchiveTagUseCase
import com.al3xiel.my_finances.feature.tags.domain.usecase.CreateTagUseCase
import com.al3xiel.my_finances.feature.tags.domain.usecase.RenameTagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `renamingId == null` = alta; al renombrar solo cambia el nombre. */
data class TagDialogState(
    val renamingId: TagId? = null,
    val kind: TagKind = TagKind.TOPIC,
    val personId: PersonId? = null,
    val name: String = "",
    val error: BusinessError? = null,
)

data class TagsUiState(
    val tags: List<Tag> = emptyList(),
    val people: List<Person> = emptyList(),
    val dialog: TagDialogState? = null,
    val notice: BusinessError? = null,
)

sealed interface TagsUiAction {
    data object ShowCreate : TagsUiAction
    data class ShowRename(val id: TagId, val currentName: String) : TagsUiAction
    data class EditName(val name: String) : TagsUiAction
    data class SelectKind(val kind: TagKind) : TagsUiAction
    data class SelectPerson(val personId: PersonId) : TagsUiAction
    data object Submit : TagsUiAction
    data object DismissDialog : TagsUiAction
    data class Archive(val id: TagId) : TagsUiAction
    data object DismissNotice : TagsUiAction
}

@HiltViewModel
class TagsViewModel @Inject constructor(
    tags: TagRepository,
    people: PersonRepository,
    private val createTag: CreateTagUseCase,
    private val renameTag: RenameTagUseCase,
    private val archiveTag: ArchiveTagUseCase,
) : ViewModel() {
    private val dialog = MutableStateFlow<TagDialogState?>(null)
    private val notice = MutableStateFlow<BusinessError?>(null)

    val uiState: StateFlow<TagsUiState> = combine(tags.observeAll(), people.observeAll(), dialog, notice) { tags, people, dialog, notice ->
        TagsUiState(
            tags = tags.filter { !it.isArchived },
            people = people.filter { !it.isArchived },
            dialog = dialog,
            notice = notice,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TagsUiState())

    fun onAction(action: TagsUiAction) {
        when (action) {
            TagsUiAction.ShowCreate -> dialog.value = TagDialogState()
            is TagsUiAction.ShowRename -> dialog.value = TagDialogState(renamingId = action.id, name = action.currentName)
            is TagsUiAction.EditName -> dialog.update { it?.copy(name = action.name, error = null) }
            is TagsUiAction.SelectKind -> dialog.update {
                it?.copy(kind = action.kind, personId = if (action.kind == TagKind.PERSON) it.personId else null, error = null)
            }
            is TagsUiAction.SelectPerson -> dialog.update { it?.copy(personId = action.personId, error = null) }
            TagsUiAction.DismissDialog -> dialog.value = null
            TagsUiAction.Submit -> submit()
            is TagsUiAction.Archive -> viewModelScope.launch { notice.value = archiveTag(action.id).errorOrNull() }
            TagsUiAction.DismissNotice -> notice.value = null
        }
    }

    private fun submit() {
        val current = dialog.value ?: return
        viewModelScope.launch {
            val outcome = if (current.renamingId != null) {
                renameTag(current.renamingId, current.name)
            } else {
                createTag(current.name, current.kind, current.personId)
            }
            val error = outcome.errorOrNull()
            dialog.update { if (error == null) null else it?.copy(error = error) }
        }
    }
}
