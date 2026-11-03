package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountArchived
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VerifyEventChainUseCaseTest {
    private val store = FakeEventStore()
    private val verify = VerifyEventChainUseCase(store)
    private val day = LocalDate.of(2026, 10, 25)
    private val a1 = AccountId("a1")

    private suspend fun seed() {
        store.append("c1", listOf(NewEvent(AccountRenamed(a1, "x"), day), NewEvent(AccountRenamed(a1, "y"), day)))
        store.append("c2", listOf(NewEvent(AccountArchived(a1), day)))
    }

    private fun replace(index: Int, change: (StoredEvent) -> StoredEvent) {
        store.events[index] = change(store.events[index])
    }

    @Test
    fun emptyAndIntactChainsAreValid() = runTest {
        assertEquals(ChainVerification.Valid(0), verify())
        seed()
        assertEquals(ChainVerification.Valid(3), verify())
    }

    @Test
    fun alteredPayloadBreaksAtThatEvent() = runTest {
        seed()
        replace(1) { it.copy(payload = it.payload.replace("y", "z")) }
        assertEquals(ChainVerification.Broken(2, ChainVerification.Reason.HASH_MISMATCH), verify())
    }

    @Test
    fun removedEventBreaksTheSequence() = runTest {
        seed()
        store.events.removeAt(1)
        assertEquals(ChainVerification.Broken(3, ChainVerification.Reason.SEQUENCE_GAP), verify())
    }

    @Test
    fun wrongLinkBreaksAtThatEvent() = runTest {
        seed()
        replace(2) { it.copy(previousHash = "forged") }
        assertEquals(ChainVerification.Broken(3, ChainVerification.Reason.BROKEN_LINK), verify())
    }

    @Test
    fun firstEventMustStartFromGenesis() = runTest {
        seed()
        replace(0) { it.copy(previousHash = "forged") }
        assertEquals(ChainVerification.Broken(1, ChainVerification.Reason.BROKEN_LINK), verify())
    }
}
