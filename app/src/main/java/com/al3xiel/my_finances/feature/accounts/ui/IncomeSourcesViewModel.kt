package com.al3xiel.my_finances.feature.accounts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.errorOrNull
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IncomeSourceDialogState(
    val name: String = "",
    val personId: PersonId? = null,
    val error: BusinessError? = null,
)

data class IncomeSourcesUiState(
    val sources: List<IncomeSource> = emptyList(),
    val people: List<Person> = emptyList(),
    val dialog: IncomeSourceDialogState? = null,
)

sealed interface IncomeSourcesUiAction {
    data object ShowCreate : IncomeSourcesUiAction
    data class EditName(val name: String) : IncomeSourcesUiAction
    data class SelectPerson(val personId: PersonId?) : IncomeSourcesUiAction
    data object Submit : IncomeSourcesUiAction
    data object DismissDialog : IncomeSourcesUiAction
}

/** Las fuentes solo se crean: no hay evento para renombrarlas ni archivarlas. */
@HiltViewModel
class IncomeSourcesViewModel @Inject constructor(
    sources: IncomeSourceRepository,
    people: PersonRepository,
    private val createIncomeSource: CreateIncomeSourceUseCase,
) : ViewModel() {
    private val dialog = MutableStateFlow<IncomeSourceDialogState?>(null)

    val uiState: StateFlow<IncomeSourcesUiState> =
        combine(sources.observeAll(), people.observeAll(), dialog) { sources, people, dialog ->
            IncomeSourcesUiState(sources = sources, people = people.filter { !it.isArchived }, dialog = dialog)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, IncomeSourcesUiState())

    fun onAction(action: IncomeSourcesUiAction) {
        when (action) {
            IncomeSourcesUiAction.ShowCreate -> dialog.value = IncomeSourceDialogState()
            is IncomeSourcesUiAction.EditName -> dialog.update { it?.copy(name = action.name, error = null) }
            is IncomeSourcesUiAction.SelectPerson -> dialog.update { it?.copy(personId = action.personId, error = null) }
            IncomeSourcesUiAction.DismissDialog -> dialog.value = null
            IncomeSourcesUiAction.Submit -> submit()
        }
    }

    private fun submit() {
        val current = dialog.value ?: return
        viewModelScope.launch {
            val error = createIncomeSource(current.name, current.personId).errorOrNull()
            dialog.update { if (error == null) null else it?.copy(error = error) }
        }
    }
}
