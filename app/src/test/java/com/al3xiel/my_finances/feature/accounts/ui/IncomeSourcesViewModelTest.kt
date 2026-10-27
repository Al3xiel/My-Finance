package com.al3xiel.my_finances.feature.accounts.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeIncomeSourceRepository
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.people.domain.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class IncomeSourcesViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val recorder = EventRecorder(store, ids, Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC))
    private val sources = FakeIncomeSourceRepository()
    private val people = FakePersonRepository()
    private val mama = PersonId("p1")

    private fun viewModel() = IncomeSourcesViewModel(sources, people, CreateIncomeSourceUseCase(sources, people, recorder, ids))

    @Test
    fun marksSystemSourcesAsNotEditable() {
        sources.sources[IncomeSourceId("s1")] = IncomeSource(IncomeSourceId("s1"), "Sueldo", IncomeSourceKind.REGULAR, null)
        sources.sources[IncomeSourceId("s2")] = IncomeSource(IncomeSourceId("s2"), "Reembolso", IncomeSourceKind.REIMBURSEMENT, null)

        val items = viewModel().uiState.value.sources

        assertEquals(listOf("Reembolso", "Sueldo"), items.map { it.name })
        assertEquals(listOf(true, false), items.map { it.kind != IncomeSourceKind.REGULAR })
    }

    @Test
    fun createsASourceOptionallyLinkedToAPerson() {
        people.people[mama] = Person(mama, "Mamá", false)
        val vm = viewModel()
        vm.onAction(IncomeSourcesUiAction.ShowCreate)
        vm.onAction(IncomeSourcesUiAction.EditName("Mamá"))
        vm.onAction(IncomeSourcesUiAction.SelectPerson(mama))
        vm.onAction(IncomeSourcesUiAction.Submit)

        assertEquals(IncomeSourceCreated(IncomeSourceId("id-1"), "Mamá", mama), store.events.single().event)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun duplicateNamesKeepTheDialogOpen() {
        sources.sources[IncomeSourceId("s1")] = IncomeSource(IncomeSourceId("s1"), "Sueldo", IncomeSourceKind.REGULAR, null)
        val vm = viewModel()
        vm.onAction(IncomeSourcesUiAction.ShowCreate)
        vm.onAction(IncomeSourcesUiAction.EditName("sueldo"))
        vm.onAction(IncomeSourcesUiAction.Submit)
        assertEquals(BusinessError.DuplicateName, vm.uiState.value.dialog?.error)
    }
}
