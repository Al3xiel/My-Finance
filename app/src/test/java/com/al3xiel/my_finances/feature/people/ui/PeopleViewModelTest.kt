package com.al3xiel.my_finances.feature.people.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.PersonArchived
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.model.event.PersonRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.usecase.ArchivePersonUseCase
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
import com.al3xiel.my_finances.feature.people.domain.usecase.RenamePersonUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PeopleViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val recorder = EventRecorder(store, ids, Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC))
    private val repository = FakePersonRepository()
    private val mama = PersonId("p1")

    private fun viewModel() = PeopleViewModel(
        repository,
        CreatePersonUseCase(repository, recorder, ids),
        RenamePersonUseCase(repository, recorder),
        ArchivePersonUseCase(repository, recorder),
    )

    @Test
    fun listsOnlyActivePeople() {
        repository.people[mama] = Person(mama, "Mamá", false)
        repository.people[PersonId("p2")] = Person(PersonId("p2"), "Papá", true)
        assertEquals(listOf("Mamá"), viewModel().uiState.value.people.map { it.alias })
    }

    @Test
    fun createRejectsDuplicatesThenSaves() {
        repository.people[mama] = Person(mama, "Mamá", false)
        val vm = viewModel()
        vm.onAction(PeopleUiAction.ShowCreate)
        vm.onAction(PeopleUiAction.EditName("mama"))
        vm.onAction(PeopleUiAction.Submit)
        assertEquals(BusinessError.DuplicateName, vm.uiState.value.dialog?.error)

        vm.onAction(PeopleUiAction.EditName("Papá"))
        vm.onAction(PeopleUiAction.Submit)
        assertEquals(PersonCreated(PersonId("id-1"), "Papá"), store.events.single().event)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun renameAndArchive() {
        repository.people[mama] = Person(mama, "Mamá", false)
        val vm = viewModel()
        vm.onAction(PeopleUiAction.ShowRename(mama, "Mamá"))
        vm.onAction(PeopleUiAction.EditName("Mami"))
        vm.onAction(PeopleUiAction.Submit)
        vm.onAction(PeopleUiAction.Archive(mama))
        assertEquals(listOf(PersonRenamed(mama, "Mami"), PersonArchived(mama)), store.events.map { it.event })
    }

    @Test
    fun archivingSomethingThatNoLongerExistsShowsANoticeUntilDismissed() {
        val vm = viewModel()
        vm.onAction(PeopleUiAction.Archive(PersonId("ghost")))
        assertEquals(BusinessError.NotFound, vm.uiState.value.notice)

        vm.onAction(PeopleUiAction.DismissNotice)
        assertNull(vm.uiState.value.notice)
    }
}
