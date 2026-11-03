package com.al3xiel.my_finances.feature.loans.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.LoanGranted
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.loans.domain.usecase.GrantLoanUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.people.domain.model.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoanEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val f = MovementFixture()
    private val people = FakePersonRepository()
    private val pepe = PersonId("pepe")

    init {
        people.people[pepe] = Person(pepe, "Pepe", false)
        people.people[PersonId("old")] = Person(PersonId("old"), "Tío", true)
        f.accounts.balances[f.bbva] = 100_000
    }

    private fun viewModel(personId: String? = null) = LoanEditorViewModel(
        personId, people, f.accounts, GrantLoanUseCase(people, f.accounts, f.rules, f.recorder, f.ids, f.writeLock), f.clock,
    )

    @Test
    fun offersActivePeopleAndAccountsAndPicksTheFirstAccount() {
        val state = viewModel().uiState.value

        assertTrue(state.loaded)
        assertEquals(listOf("Pepe"), state.personOptions.map { it.label })
        assertEquals(listOf("BBVA", "Efectivo"), state.accountOptions.map { it.label })
        assertEquals(f.bbva, state.accountId)
        assertNull(state.personId)
    }

    @Test
    fun aPersonFromTheRouteIsPreselected() {
        assertEquals(pepe, viewModel(pepe.value).uiState.value.personId)
    }

    @Test
    fun savingGrantsTheLoanWithTheChosenData() {
        val vm = viewModel(pepe.value)
        vm.onAction(LoanEditorUiAction.EditAmount("50"))
        vm.onAction(LoanEditorUiAction.EditNote(" taxi "))

        vm.onAction(LoanEditorUiAction.Save)

        assertEquals(LoanGranted(LoanId("id-1"), MovementId("id-2"), pepe, f.bbva, Money(5_000, CurrencyCode.PEN), "taxi"), f.store.events.single().event)
        assertTrue(vm.uiState.value.saved)
    }

    @Test
    fun thePersonAndTheAmountAreRequired() {
        val vm = viewModel()

        vm.onAction(LoanEditorUiAction.EditAmount("50"))
        vm.onAction(LoanEditorUiAction.Save)
        assertEquals(BusinessError.PersonRequired, vm.uiState.value.error)

        vm.onAction(LoanEditorUiAction.SelectPerson(pepe))
        vm.onAction(LoanEditorUiAction.EditAmount(""))
        vm.onAction(LoanEditorUiAction.Save)
        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)
        assertEquals(0, f.store.events.size)
        assertFalse(vm.uiState.value.saved)
    }

    @Test
    fun lendingMoreThanTheBalanceWarnsAndSavesWhenConfirmed() {
        f.accounts.balances[f.bbva] = 1_000
        val vm = viewModel(pepe.value)
        vm.onAction(LoanEditorUiAction.EditAmount("50"))

        vm.onAction(LoanEditorUiAction.Save)
        assertEquals(BusinessError.WouldGoNegative(-4_000), vm.uiState.value.negativeWarning)
        assertEquals(0, f.store.events.size)

        vm.onAction(LoanEditorUiAction.ConfirmNegativeBalance)

        assertEquals(1, f.store.events.size)
        assertTrue(vm.uiState.value.saved)
        assertNull(vm.uiState.value.negativeWarning)
    }
}
