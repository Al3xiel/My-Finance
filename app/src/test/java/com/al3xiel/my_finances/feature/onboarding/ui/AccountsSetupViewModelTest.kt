package com.al3xiel.my_finances.feature.onboarding.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateInitialAccountsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AccountsSetupViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val accounts = FakeAccountRepository()

    private fun viewModel(eventStore: EventStore = store) =
        AccountsSetupViewModel(CreateInitialAccountsUseCase(accounts, EventRecorder(eventStore, ids, clock), ids))

    private fun names() = store.events.map { (it.event as AccountCreated).name }

    @Test
    fun startsWithTheSuggestedList() {
        val rows = viewModel().uiState.value.rows
        assertEquals(listOf("BBVA", "BCP", "Efectivo"), rows.map { it.name })
        assertEquals(listOf(AccountType.BANK, AccountType.BANK, AccountType.CASH), rows.map { it.type })
    }

    @Test
    fun createsTheEditedListAndSignalsCompletion() {
        val vm = viewModel()
        val keys = vm.uiState.value.rows.map { it.key }
        vm.onAction(AccountsSetupUiAction.Remove(keys[1]))
        vm.onAction(AccountsSetupUiAction.EditName(keys[0], "BBVA Soles"))
        vm.onAction(AccountsSetupUiAction.EditBalance(keys[0], "1250.50"))
        vm.onAction(AccountsSetupUiAction.Submit)

        val created = store.events.map { it.event as AccountCreated }
        assertEquals(listOf("BBVA Soles", "Efectivo"), created.map { it.name })
        assertEquals(Money(125_050, CurrencyCode.PEN), created[0].openingBalance)
        assertEquals(Money(0, CurrencyCode.PEN), created[1].openingBalance)
        assertTrue(vm.uiState.value.created)
    }

    @Test
    fun removingEveryRowIsRejectedWithoutEmitting() {
        val vm = viewModel()
        vm.uiState.value.rows.forEach { vm.onAction(AccountsSetupUiAction.Remove(it.key)) }
        vm.onAction(AccountsSetupUiAction.Submit)

        assertEquals(BusinessError.NoAccounts, vm.uiState.value.error)
        assertTrue(store.events.isEmpty())
        assertFalse(vm.uiState.value.created)
    }

    @Test
    fun repeatedNameIsReportedAndNothingIsCreated() {
        val vm = viewModel()
        val keys = vm.uiState.value.rows.map { it.key }
        vm.onAction(AccountsSetupUiAction.EditName(keys[1], "bbva"))
        vm.onAction(AccountsSetupUiAction.Submit)

        assertEquals(BusinessError.DuplicateName, vm.uiState.value.error)
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun invalidBalanceIsReportedAndEditingClearsTheError() {
        val vm = viewModel()
        val key = vm.uiState.value.rows.first().key
        vm.onAction(AccountsSetupUiAction.EditBalance(key, "abc"))
        vm.onAction(AccountsSetupUiAction.Submit)

        assertEquals(BusinessError.InvalidAmount, vm.uiState.value.error)
        assertTrue(store.events.isEmpty())

        vm.onAction(AccountsSetupUiAction.EditBalance(key, "10"))
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun dataLayerFailureShowsUnexpectedAndRetryCreatesEachAccountOnce() {
        var failNext = true
        val flaky = object : EventStore by store {
            override suspend fun append(correlationId: String, events: List<NewEvent>): List<StoredEvent> {
                if (failNext) {
                    failNext = false
                    error("disk full")
                }
                return store.append(correlationId, events)
            }
        }
        val vm = viewModel(flaky)
        vm.onAction(AccountsSetupUiAction.Submit)

        assertEquals(BusinessError.Unexpected, vm.uiState.value.error)
        assertFalse(vm.uiState.value.saving)
        assertTrue(store.events.isEmpty())

        vm.onAction(AccountsSetupUiAction.Submit)
        assertEquals(listOf("BBVA", "BCP", "Efectivo"), names())
        assertTrue(vm.uiState.value.created)
    }

    @Test
    fun addedRowsAreBankAccountsWithEmptyName() {
        val vm = viewModel()
        vm.onAction(AccountsSetupUiAction.Add)
        val rows = vm.uiState.value.rows
        assertEquals("", rows.last().name)
        assertEquals(AccountType.BANK, rows.last().type)
        assertEquals(4, rows.map { it.key }.distinct().size)
    }
}
