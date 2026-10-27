package com.al3xiel.my_finances.feature.accounts.ui

import com.al3xiel.my_finances.core.MainDispatcherRule
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.LedgerEntryKind
import com.al3xiel.my_finances.domain.model.event.AccountArchived
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.accounts.domain.usecase.ArchiveAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RenameAccountUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class AccountsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC)
    private val recorder = EventRecorder(store, ids, clock)
    private val repository = FakeAccountRepository()

    private val bbva = AccountId("bbva")

    private fun account(id: AccountId, name: String, archived: Boolean = false, balance: Long = 0) {
        repository.accounts[id] = Account(id, name, AccountType.BANK, CurrencyCode.PEN, null, archived)
        repository.balances[id] = balance
    }

    private fun viewModel() = AccountsViewModel(
        repository,
        CreateAccountUseCase(repository, recorder, ids),
        RenameAccountUseCase(repository, recorder),
        ArchiveAccountUseCase(repository, recorder),
    )

    @Test
    fun listsActiveAccountsFirstWithTheirBalances() {
        account(AccountId("old"), "Antigua", archived = true)
        account(bbva, "BBVA", balance = 12_340)

        val list = viewModel().uiState.value.accounts

        assertEquals(listOf("BBVA", "Antigua"), list.map { it.account.name })
        assertEquals(12_340L, list.first().balanceMinor)
    }

    @Test
    fun createsAnAccountWithTheTypedOpeningBalance() {
        val vm = viewModel()
        vm.onAction(AccountsUiAction.ShowCreate)
        vm.onAction(AccountsUiAction.EditName("BCP"))
        vm.onAction(AccountsUiAction.SelectType(AccountType.DIGITAL_WALLET))
        vm.onAction(AccountsUiAction.EditOpeningBalance("1250.5"))
        vm.onAction(AccountsUiAction.Submit)

        val event = store.events.single().event as AccountCreated
        assertEquals("BCP", event.name)
        assertEquals(AccountType.DIGITAL_WALLET, event.accountType)
        assertEquals(Money(125_050, CurrencyCode.PEN), event.openingBalance)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun anEmptyOpeningBalanceMeansZero() {
        val vm = viewModel()
        vm.onAction(AccountsUiAction.ShowCreate)
        vm.onAction(AccountsUiAction.EditName("Efectivo"))
        vm.onAction(AccountsUiAction.Submit)
        assertEquals(Money(0, CurrencyCode.PEN), (store.events.single().event as AccountCreated).openingBalance)
    }

    @Test
    fun aDuplicateNameKeepsTheDialogOpenWithTheError() {
        account(bbva, "BBVA")
        val vm = viewModel()
        vm.onAction(AccountsUiAction.ShowCreate)
        vm.onAction(AccountsUiAction.EditName("bbva"))
        vm.onAction(AccountsUiAction.Submit)

        assertEquals(BusinessError.DuplicateName, vm.uiState.value.dialog?.error)
        assertEquals(0, store.events.size)
    }

    @Test
    fun renamePrefillsTheName() {
        account(bbva, "BBVA")
        val vm = viewModel()
        vm.onAction(AccountsUiAction.ShowRename(bbva, "BBVA"))
        assertEquals("BBVA", vm.uiState.value.dialog?.name)
        vm.onAction(AccountsUiAction.EditName("BBVA Sueldo"))
        vm.onAction(AccountsUiAction.Submit)
        assertEquals(AccountRenamed(bbva, "BBVA Sueldo"), store.events.single().event)
    }

    @Test
    fun archivingAnAccountWithBalanceShowsANoticeInsteadOfEmitting() {
        account(bbva, "BBVA", balance = 500)
        val vm = viewModel()
        vm.onAction(AccountsUiAction.Archive(bbva))

        assertEquals(BusinessError.BalanceNotZero, vm.uiState.value.notice)
        assertEquals(0, store.events.size)

        vm.onAction(AccountsUiAction.DismissNotice)
        assertNull(vm.uiState.value.notice)

        repository.balances[bbva] = 0
        vm.onAction(AccountsUiAction.Archive(bbva))
        assertEquals(AccountArchived(bbva), store.events.single().event)
    }

    @Test
    fun detailShowsLedgerAndBalanceAtAPickedDate() {
        account(bbva, "BBVA", balance = 700)
        repository.ledger[bbva] = listOf(
            line("2-0", bbva, -300, LedgerEntryKind.EXPENSE, LocalDate.of(2026, 10, 5)),
            line("1-0", bbva, 1_000, LedgerEntryKind.OPENING, LocalDate.of(2026, 10, 1)),
            line("3-0", bbva, -50, LedgerEntryKind.EXPENSE, LocalDate.of(2026, 10, 6), voided = true),
        )
        val detail = AccountDetailViewModel(bbva.value, repository, clock)

        assertEquals(700L, detail.uiState.value.account?.balanceMinor)
        assertEquals(3, detail.uiState.value.ledger.size)
        assertNull(detail.uiState.value.balanceAsOfMinor)
        assertEquals(LocalDate.of(2026, 10, 26), detail.uiState.value.today)

        detail.onAction(AccountDetailUiAction.PickAsOf(LocalDate.of(2026, 10, 3)))
        assertEquals(1_000L, detail.uiState.value.balanceAsOfMinor)

        detail.onAction(AccountDetailUiAction.PickAsOf(LocalDate.of(2026, 10, 10)))
        assertEquals(700L, detail.uiState.value.balanceAsOfMinor)

        detail.onAction(AccountDetailUiAction.ClearAsOf)
        assertNull(detail.uiState.value.asOf)
    }

    private fun line(id: String, account: AccountId, delta: Long, kind: LedgerEntryKind, on: LocalDate, voided: Boolean = false) =
        LedgerLine(id, id.substringBefore('-').toLong(), null, delta, kind, on, voided)
}
