package com.al3xiel.my_finances.feature.categories.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.event.CategoryArchived
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.CategoryRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeCategoryRepository
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.categories.domain.usecase.ArchiveCategoryUseCase
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.categories.domain.usecase.RenameCategoryUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CategoriesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val recorder = EventRecorder(store, ids, Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC))
    private val repository = FakeCategoryRepository()

    private val food = CategoryId("c1")
    private val outings = CategoryId("c2")

    private fun category(id: CategoryId, name: String, parent: CategoryId? = null, archived: Boolean = false) =
        Category(id, name, parent, archived).also { repository.categories[id] = it }

    private fun viewModel() = CategoriesViewModel(
        repository,
        CreateCategoryUseCase(repository, recorder, ids),
        RenameCategoryUseCase(repository, recorder),
        ArchiveCategoryUseCase(repository, recorder),
    )

    @Test
    fun showsActiveCategoriesAsAOneLevelTree() {
        category(food, "Comida")
        category(outings, "Salidas", parent = food)
        category(CategoryId("c3"), "Vieja", archived = true)
        category(CategoryId("c4"), "Hogar")

        val nodes = viewModel().uiState.value.nodes

        assertEquals(listOf("Comida", "Hogar"), nodes.map { it.category.name })
        assertEquals(listOf("Salidas"), nodes.first().children.map { it.name })
    }

    @Test
    fun createsARootCategoryAndClosesTheDialog() {
        val vm = viewModel()
        vm.onAction(CategoriesUiAction.ShowCreate(parentId = null))
        vm.onAction(CategoriesUiAction.EditName("Ocio"))
        vm.onAction(CategoriesUiAction.Submit)

        val event = store.events.single().event as CategoryCreated
        assertEquals("Ocio", event.name)
        assertNull(event.parentCategoryId)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun createsASubcategoryUnderTheGivenParent() {
        category(food, "Comida")
        val vm = viewModel()
        vm.onAction(CategoriesUiAction.ShowCreate(parentId = food))
        vm.onAction(CategoriesUiAction.EditName("Restaurantes"))
        vm.onAction(CategoriesUiAction.Submit)

        assertEquals(food, (store.events.single().event as CategoryCreated).parentCategoryId)
    }

    @Test
    fun aRejectedNameKeepsTheDialogOpenWithTheError() {
        category(food, "Comida")
        val vm = viewModel()
        vm.onAction(CategoriesUiAction.ShowCreate(parentId = null))
        vm.onAction(CategoriesUiAction.EditName("comida"))
        vm.onAction(CategoriesUiAction.Submit)

        assertEquals(BusinessError.DuplicateName, vm.uiState.value.dialog?.error)
        assertEquals(0, store.events.size)

        vm.onAction(CategoriesUiAction.EditName("Comidas"))
        assertNull(vm.uiState.value.dialog?.error)
    }

    @Test
    fun renamePrefillsTheCurrentNameAndEmitsTheEvent() {
        category(food, "Comida")
        val vm = viewModel()
        vm.onAction(CategoriesUiAction.ShowRename(food, "Comida"))
        assertEquals("Comida", vm.uiState.value.dialog?.name)

        vm.onAction(CategoriesUiAction.EditName("Alimentos"))
        vm.onAction(CategoriesUiAction.Submit)

        assertEquals(CategoryRenamed(food, "Alimentos"), store.events.single().event)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun archiveEmitsTheEventAndDismissClosesTheDialog() {
        category(food, "Comida")
        val vm = viewModel()
        vm.onAction(CategoriesUiAction.Archive(food))
        assertEquals(CategoryArchived(food), store.events.single().event)

        vm.onAction(CategoriesUiAction.ShowCreate(parentId = null))
        vm.onAction(CategoriesUiAction.DismissDialog)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun archivingSomethingThatNoLongerExistsShowsANoticeUntilDismissed() {
        val vm = viewModel()
        vm.onAction(CategoriesUiAction.Archive(CategoryId("ghost")))
        assertEquals(BusinessError.NotFound, vm.uiState.value.notice)

        vm.onAction(CategoriesUiAction.DismissNotice)
        assertNull(vm.uiState.value.notice)
    }
}
