package com.al3xiel.my_finances.feature.accounts.domain

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateInitialAccountsUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.InitialAccountDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CreateInitialAccountsUseCaseTest {
    private val store = FakeEventStore()
    private val ids = SequentialIdGenerator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val accounts = FakeAccountRepository()
    private val create = CreateInitialAccountsUseCase(accounts, EventRecorder(store, ids, clock), ids)

    private fun draft(name: String, minor: Long = 0, type: AccountType = AccountType.BANK) =
        InitialAccountDraft(name, type, Money(minor, CurrencyCode.PEN))

    @Test
    fun createsAllAccountsInOneCall() = runTest {
        val result = create(listOf(draft("BBVA", 100_00), draft("Efectivo", 50_00, AccountType.CASH)))

        assertTrue(result is Outcome.Ok)
        val events = store.events.map { it.event as AccountCreated }
        assertEquals(listOf("BBVA", "Efectivo"), events.map { it.name })
        assertEquals(1, store.events.map { it.correlationId }.distinct().size)
        assertEquals(Money(100_00, CurrencyCode.PEN), events[0].openingBalance)
    }

    @Test
    fun emptyListFailsWithoutEmitting() = runTest {
        assertEquals(Outcome.Failed(BusinessError.NoAccounts), create(emptyList()))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun oneInvalidDraftMeansNothingIsCreated() = runTest {
        assertEquals(Outcome.Failed(BusinessError.NegativeAmount), create(listOf(draft("BBVA"), draft("BCP", -1))))
        assertEquals(Outcome.Failed(BusinessError.BlankName), create(listOf(draft("BBVA"), draft("  "))))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun duplicatesWithinTheListFail() = runTest {
        assertEquals(Outcome.Failed(BusinessError.DuplicateName), create(listOf(draft("BBVA"), draft(" bbva "))))
        assertTrue(store.events.isEmpty())
    }

    @Test
    fun nameTakenByActiveAccountFailsButArchivedDoesNot() = runTest {
        val id = AccountId("x")
        accounts.accounts[id] = Account(id, "BCP", AccountType.BANK, CurrencyCode.PEN, null, false)
        assertEquals(Outcome.Failed(BusinessError.DuplicateName), create(listOf(draft("bcp"))))

        accounts.accounts[id] = Account(id, "BCP", AccountType.BANK, CurrencyCode.PEN, null, true)
        assertTrue(create(listOf(draft("BCP"))) is Outcome.Ok)
    }
}
