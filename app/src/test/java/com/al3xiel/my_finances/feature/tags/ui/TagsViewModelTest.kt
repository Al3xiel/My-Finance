package com.al3xiel.my_finances.feature.tags.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.TagArchived
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.model.event.TagRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.accounts.domain.FakeTagRepository
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import com.al3xiel.my_finances.feature.tags.domain.usecase.ArchiveTagUseCase
import com.al3xiel.my_finances.feature.tags.domain.usecase.CreateTagUseCase
import com.al3xiel.my_finances.feature.tags.domain.usecase.RenameTagUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class TagsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val recorder = EventRecorder(store, ids, Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC))
    private val tags = FakeTagRepository()
    private val people = FakePersonRepository()

    private val mama = PersonId("p1")
    private val trip = TagId("t1")

    private fun viewModel() = TagsViewModel(
        tags,
        people,
        CreateTagUseCase(tags, people, recorder, ids),
        RenameTagUseCase(tags, recorder),
        ArchiveTagUseCase(tags, recorder),
    )

    @Test
    fun listsActiveTagsAndActivePeopleForThePicker() {
        tags.tags[trip] = Tag(trip, "Viaje", TagKind.TOPIC, null, false)
        tags.tags[TagId("t2")] = Tag(TagId("t2"), "Vieja", TagKind.TOPIC, null, true)
        people.people[mama] = Person(mama, "Mamá", false)
        people.people[PersonId("p2")] = Person(PersonId("p2"), "Papá", true)

        val state = viewModel().uiState.value

        assertEquals(listOf("Viaje"), state.tags.map { it.name })
        assertEquals(listOf("Mamá"), state.people.map { it.alias })
    }

    @Test
    fun createsATopicTagByDefault() {
        val vm = viewModel()
        vm.onAction(TagsUiAction.ShowCreate)
        assertEquals(TagKind.TOPIC, vm.uiState.value.dialog?.kind)
        vm.onAction(TagsUiAction.EditName("Viaje"))
        vm.onAction(TagsUiAction.Submit)

        assertEquals(TagCreated(TagId("id-1"), "Viaje", TagKind.TOPIC, null), store.events.single().event)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun aPersonTagNeedsAPersonAndSavesWithThePickedOne() {
        people.people[mama] = Person(mama, "Mamá", false)
        val vm = viewModel()
        vm.onAction(TagsUiAction.ShowCreate)
        vm.onAction(TagsUiAction.SelectKind(TagKind.PERSON))
        vm.onAction(TagsUiAction.EditName("Mamá"))
        vm.onAction(TagsUiAction.Submit)

        assertEquals(BusinessError.PersonRequired, vm.uiState.value.dialog?.error)
        assertEquals(0, store.events.size)

        vm.onAction(TagsUiAction.SelectPerson(mama))
        vm.onAction(TagsUiAction.Submit)
        assertEquals(TagCreated(TagId("id-1"), "Mamá", TagKind.PERSON, mama), store.events.single().event)
    }

    @Test
    fun switchingBackToATopicDropsThePerson() {
        val vm = viewModel()
        vm.onAction(TagsUiAction.ShowCreate)
        vm.onAction(TagsUiAction.SelectKind(TagKind.PERSON))
        vm.onAction(TagsUiAction.SelectPerson(mama))
        vm.onAction(TagsUiAction.SelectKind(TagKind.TOPIC))
        assertNull(vm.uiState.value.dialog?.personId)
    }

    @Test
    fun renameAndArchive() {
        tags.tags[trip] = Tag(trip, "Viaje", TagKind.TOPIC, null, false)
        val vm = viewModel()
        vm.onAction(TagsUiAction.ShowRename(trip, "Viaje"))
        vm.onAction(TagsUiAction.EditName("Viajes"))
        vm.onAction(TagsUiAction.Submit)
        vm.onAction(TagsUiAction.Archive(trip))

        assertEquals(listOf(TagRenamed(trip, "Viajes"), TagArchived(trip)), store.events.map { it.event })
    }

    @Test
    fun archivingSomethingThatNoLongerExistsShowsANoticeUntilDismissed() {
        val vm = viewModel()
        vm.onAction(TagsUiAction.Archive(TagId("ghost")))
        assertEquals(BusinessError.NotFound, vm.uiState.value.notice)

        vm.onAction(TagsUiAction.DismissNotice)
        assertNull(vm.uiState.value.notice)
    }
}
