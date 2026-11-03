package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.EntityRef
import com.al3xiel.my_finances.domain.model.event.EntityType
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveAliasesUseCaseTest {
    private val store = FakeEventStore()
    private val day = LocalDate.of(2026, 10, 25)
    private val refs = mapOf("Account-A" to EntityRef(EntityType.ACCOUNT, "a1"), "Person-1" to EntityRef(EntityType.PERSON, "p1"))

    private suspend fun seed() {
        store.append(
            "c",
            listOf(
                AccountCreated(AccountId("a1"), "Primero", AccountType.BANK, CurrencyCode.PEN, null, Money(0, CurrencyCode.PEN)),
                PersonCreated(PersonId("p1"), "Ana"),
                AccountRenamed(AccountId("a1"), "Segundo"),
                AccountRenamed(AccountId("a1"), "Tercero"),
            ).map { NewEvent(it, day) },
        )
    }

    @Test
    fun theLastNameUpToTheCutWins() = runTest {
        seed()
        val aliases = ResolveAliasesUseCase(store)(refs, upToSequence = 3)
        assertEquals(mapOf("Account-A" to "Segundo", "Person-1" to "Ana"), aliases)
    }

    @Test
    fun aRenameAfterTheCutIsNeverRevealed() = runTest {
        seed()
        assertEquals("Primero", ResolveAliasesUseCase(store)(refs, upToSequence = 2)["Account-A"])
    }

    @Test
    fun anEntityWithoutANameYetIsSimplyOmitted() = runTest {
        seed()
        assertEquals(emptyMap<String, String>(), ResolveAliasesUseCase(store)(refs, upToSequence = 0))
    }

    @Test
    fun theEntityTypeMustMatchSoAnIdFromAnotherKindNeverLeaks() = runTest {
        seed()
        val wrongKind = mapOf("Person-1" to EntityRef(EntityType.PERSON, "a1"))
        assertEquals(emptyMap<String, String>(), ResolveAliasesUseCase(store)(wrongKind, upToSequence = 4))
    }
}
