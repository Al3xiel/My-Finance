package com.al3xiel.my_finances.feature.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.people.domain.usecase.ArchivePersonUseCase
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
import com.al3xiel.my_finances.feature.people.domain.usecase.RenamePersonUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonDialogState(
    val renamingId: PersonId? = null,
    val name: String = "",
    val error: BusinessError? = null,
)

data class PeopleUiState(
    val people: List<Person> = emptyList(),
    val dialog: PersonDialogState? = null,
    val notice: BusinessError? = null,
)

sealed interface PeopleUiAction {
    data object ShowCreate : PeopleUiAction
    data class ShowRename(val id: PersonId, val currentAlias: String) : PeopleUiAction
    data class EditName(val name: String) : PeopleUiAction
    data object Submit : PeopleUiAction
    data object DismissDialog : PeopleUiAction
    data class Archive(val id: PersonId) : PeopleUiAction
    data object DismissNotice : PeopleUiAction
}

@HiltViewModel
class PeopleViewModel @Inject constructor(
    people: PersonRepository,
    private val createPerson: CreatePersonUseCase,
    private val renamePerson: RenamePersonUseCase,
    private val archivePerson: ArchivePersonUseCase,
) : ViewModel() {
    private val dialog = MutableStateFlow<PersonDialogState?>(null)
    private val notice = MutableStateFlow<BusinessError?>(null)

    val uiState: StateFlow<PeopleUiState> = combine(people.observeAll(), dialog, notice) { all, dialog, notice ->
        PeopleUiState(people = all.filter { !it.isArchived }, dialog = dialog, notice = notice)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PeopleUiState())

    fun onAction(action: PeopleUiAction) {
        when (action) {
            PeopleUiAction.ShowCreate -> dialog.value = PersonDialogState()
            is PeopleUiAction.ShowRename -> dialog.value = PersonDialogState(renamingId = action.id, name = action.currentAlias)
            is PeopleUiAction.EditName -> dialog.update { it?.copy(name = action.name, error = null) }
            PeopleUiAction.DismissDialog -> dialog.value = null
            PeopleUiAction.Submit -> submit()
            is PeopleUiAction.Archive -> viewModelScope.launch { notice.value = archivePerson(action.id).errorOrNull() }
            PeopleUiAction.DismissNotice -> notice.value = null
        }
    }

    private fun submit() {
        val current = dialog.value ?: return
        viewModelScope.launch {
            val outcome = if (current.renamingId != null) {
                renamePerson(current.renamingId, current.name)
            } else {
                createPerson(current.name)
            }
            val error = outcome.errorOrNull()
            dialog.update { if (error == null) null else it?.copy(error = error) }
        }
    }
}
