package com.al3xiel.my_finances.feature.pools.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolRenamed
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeCategoryRepository
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.accounts.domain.FakeTagRepository
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.usecase.CreatePoolUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import com.al3xiel.my_finances.feature.pools.domain.usecase.RenamePoolUseCase
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class PoolEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val recorder = EventRecorder(store, ids, Clock.fixed(Instant.parse("2026-10-27T15:00:00Z"), ZoneOffset.UTC))
    private val pools = FakePoolRepository()
    private val people = FakePersonRepository()
    private val categories = FakeCategoryRepository()
    private val tags = FakeTagRepository()

    private val mom = PersonId("mom")
    private val pets = CategoryId("pets")
    private val dogTag = TagId("dog")
    private val dogs = PoolId("dogs")

    init {
        people.people[mom] = Person(mom, "Mamá", false)
        people.people[PersonId("old")] = Person(PersonId("old"), "Tío", true)
        categories.categories[pets] = Category(pets, "Mascotas", null, false)
        tags.tags[dogTag] = Tag(dogTag, "Perros", TagKind.TOPIC, null, false)
    }

    private fun viewModel(poolId: String? = null) = PoolEditorViewModel(
        poolId, pools, people, categories, tags,
        CreatePoolUseCase(pools, people, categories, tags, recorder, ids),
        RenamePoolUseCase(pools, recorder),
    )

    @Test
    fun offersOnlyActivePeopleAndTheCatalogs() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertFalse(state.isEditing)
        assertEquals(listOf("Mamá"), state.personOptions.map { it.label })
        assertEquals(listOf("Mascotas"), state.categoryOptions.map { it.label })
        assertEquals(listOf("Perros"), state.tagOptions.map { it.label })
        assertEquals(PoolKind.PERMANENT, state.kind)
    }

    @Test
    fun theResponsibleIsRequiredToSave() {
        val vm = viewModel()
        vm.onAction(PoolEditorUiAction.EditName("Perros"))

        vm.onAction(PoolEditorUiAction.Save)

        assertEquals(BusinessError.PersonRequired, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saved)
        assertEquals(0, store.events.size)
    }

    @Test
    fun createsThePoolWithResponsibleKindAndDefaults() {
        val vm = viewModel()
        vm.onAction(PoolEditorUiAction.EditName(" Perros – mamá "))
        vm.onAction(PoolEditorUiAction.SelectPerson(mom))
        vm.onAction(PoolEditorUiAction.SelectKind(PoolKind.EVENT))
        vm.onAction(PoolEditorUiAction.SelectCategory(pets))
        vm.onAction(PoolEditorUiAction.ToggleTag(dogTag))

        vm.onAction(PoolEditorUiAction.Save)

        assertEquals(
            ReimbursementPoolCreated(PoolId("id-1"), "Perros – mamá", mom, PoolKind.EVENT, pets, listOf(dogTag)),
            store.events.single().event,
        )
        assertTrue(vm.uiState.value.saved)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun aTagCanBeToggledOffAndTheCategoryCleared() {
        val vm = viewModel()
        vm.onAction(PoolEditorUiAction.ToggleTag(dogTag))
        vm.onAction(PoolEditorUiAction.ToggleTag(dogTag))
        vm.onAction(PoolEditorUiAction.SelectCategory(pets))
        vm.onAction(PoolEditorUiAction.SelectCategory(null))

        assertTrue(vm.uiState.value.tagIds.isEmpty())
        assertNull(vm.uiState.value.categoryId)
    }

    @Test
    fun aRejectedSaveShowsTheErrorAndEditingClearsIt() {
        pools.pools[dogs] = Pool(dogs, "Perros", mom, PoolKind.PERMANENT, null, emptyList(), false)
        val vm = viewModel()
        vm.onAction(PoolEditorUiAction.EditName("perros"))
        vm.onAction(PoolEditorUiAction.SelectPerson(mom))

        vm.onAction(PoolEditorUiAction.Save)
        assertEquals(BusinessError.DuplicateName, vm.uiState.value.error)

        vm.onAction(PoolEditorUiAction.EditName("Gatos"))
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun editingLoadsTheNameLocksTheRestAndOnlyRenames() {
        pools.pools[dogs] = Pool(dogs, "Perros", mom, PoolKind.EVENT, pets, listOf(dogTag), false)
        val vm = viewModel(dogs.value)

        val state = vm.uiState.value
        assertTrue(state.isEditing)
        assertEquals("Perros", state.name)
        assertEquals(mom, state.personId)
        assertEquals(PoolKind.EVENT, state.kind)

        vm.onAction(PoolEditorUiAction.EditName("Perritos"))
        vm.onAction(PoolEditorUiAction.Save)

        assertEquals(ReimbursementPoolRenamed(dogs, "Perritos"), store.events.single().event)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun editingAnUnknownPoolIsNotFound() {
        assertTrue(viewModel("ghost").uiState.value.notFound)
    }
}
