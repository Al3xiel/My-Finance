package com.al3xiel.my_finances.feature.people.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.people.domain.model.PersonDetail
import com.al3xiel.my_finances.feature.people.domain.repository.PersonDetailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PersonDetailUiState(
    val loaded: Boolean = false,
    val detail: PersonDetail? = null,
) {
    val notFound: Boolean get() = loaded && detail == null
}

/** S35: saldo con la persona, sus cúmulos, deudas, reembolsos y gastos. Solo se lee; las acciones las resuelve la navegación. */
@HiltViewModel(assistedFactory = PersonDetailViewModel.Factory::class)
class PersonDetailViewModel @AssistedInject constructor(
    @Assisted personIdValue: String,
    people: PersonDetailRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(personIdValue: String): PersonDetailViewModel
    }

    // KSP no puede nombrar una fábrica asistida con un value class: llega el texto y se envuelve aquí.
    val uiState: StateFlow<PersonDetailUiState> = people.observePersonDetail(PersonId(personIdValue))
        .map { PersonDetailUiState(loaded = true, detail = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PersonDetailUiState())
}
