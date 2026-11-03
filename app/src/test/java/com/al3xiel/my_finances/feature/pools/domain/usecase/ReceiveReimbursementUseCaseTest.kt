package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.ReimbursementId
import com.al3xiel.my_finances.domain.model.event.AllocationData
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.ReimbursementReceived
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.pools.domain.rules.PendingPart
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ReceiveReimbursementUseCaseTest {
    private val f = MovementFixture()
    private val pools = FakePoolRepository()
    private val people = FakePersonRepository()
    private val receive = ReceiveReimbursementUseCase(pools, people, f.rules, f.recorder, f.ids, f.writeLock)

    private val mom = PersonId("mom")
    private val dogs = PoolId("dogs")
    private val cats = PoolId("cats")

    init {
        people.people[mom] = Person(mom, "Mamá", false)
    }

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun pending(pool: PoolId, part: String, day: Int, seq: Long, minor: Long) {
        pools.pendingByPerson.getOrPut(mom) { mutableListOf() } += pool to PendingPart(PartId(part), LocalDate.of(2026, 10, day), seq, pen(minor))
    }

    private fun failed(error: BusinessError) = Outcome.Failed(error)

    @Test
    fun oldestFirstStoresTheAllocationsItComputedInTheEvent() = runTest {
        pending(dogs, "p2", 12, 7, 200); pending(dogs, "p1", 3, 4, 120)

        val result = receive(mom, f.bbva, pen(250), AllocationStrategy.OLDEST_FIRST, null, emptyList(), "  efectivo ", f.today)

        assertEquals(Outcome.Ok(MovementId("id-2")), result)
        assertEquals(
            ReimbursementReceived(
                ReimbursementId("id-1"), MovementId("id-2"), mom, f.bbva, pen(250), AllocationStrategy.OLDEST_FIRST,
                listOf(AllocationData(PartId("p1"), pen(120)), AllocationData(PartId("p2"), pen(130))), "efectivo",
            ),
            f.store.events.single().event,
        )
        assertEquals(f.today, f.store.events.single().occurredOn)
    }

    @Test
    fun fromAPoolOnlyItsOwnPartsAreConsidered() = runTest {
        pending(dogs, "d1", 5, 1, 100); pending(cats, "c1", 1, 2, 100)

        receive(mom, f.bbva, pen(100), AllocationStrategy.OLDEST_FIRST, dogs, emptyList(), null, f.today)
        assertEquals(listOf(PartId("d1")), (f.store.events.single().event as ReimbursementReceived).allocations.map { it.partId })

        assertEquals(failed(BusinessError.ExceedsPending), receive(mom, f.bbva, pen(101), AllocationStrategy.OLDEST_FIRST, dogs, emptyList(), null, f.today))
    }

    @Test
    fun fromThePlusButtonAllThePersonsPoolsAreConsidered() = runTest {
        pending(dogs, "d1", 5, 1, 100); pending(cats, "c1", 1, 2, 100)

        receive(mom, f.bbva, pen(150), AllocationStrategy.OLDEST_FIRST, null, emptyList(), null, f.today)

        val allocations = (f.store.events.single().event as ReimbursementReceived).allocations
        assertEquals(listOf(PartId("c1") to 100L, PartId("d1") to 50L), allocations.map { it.partId to it.amount.amountMinor })
    }

    @Test
    fun anAmountAbovePendingIsRejected() = runTest {
        pending(dogs, "p1", 3, 4, 120)

        assertEquals(failed(BusinessError.ExceedsPending), receive(mom, f.bbva, pen(121), AllocationStrategy.OLDEST_FIRST, null, emptyList(), null, f.today))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun manualAllocationsMustSumTheAmountAndFitEachPart() = runTest {
        pending(dogs, "p1", 3, 4, 120); pending(dogs, "p2", 4, 5, 80)
        suspend fun manual(minor: Long, vararg a: Pair<String, Long>) =
            receive(mom, f.bbva, pen(minor), AllocationStrategy.MANUAL, null, a.map { (p, m) -> AllocationData(PartId(p), pen(m)) }, null, f.today)

        assertEquals(failed(BusinessError.PartsMismatch), manual(100, "p1" to 60))
        assertEquals(failed(BusinessError.ExceedsPending), manual(130, "p1" to 130))
        assertEquals(failed(BusinessError.InvalidPart), manual(10, "ghost" to 10))
        assertEquals(failed(BusinessError.InvalidPart), manual(20, "p1" to 10, "p1" to 10))
        assertEquals(failed(BusinessError.InvalidAmount), manual(0))
        assertEquals(0, f.store.events.size)

        assertEquals(Outcome.Ok(MovementId("id-2")), manual(90, "p1" to 50, "p2" to 40))
        val event = f.store.events.single().event as ReimbursementReceived
        assertEquals(AllocationStrategy.MANUAL, event.strategy)
        assertEquals(listOf(50L, 40L), event.allocations.map { it.amount.amountMinor })
    }

    @Test
    fun rejectsUnknownPersonArchivedAccountFutureDateAndBadAmount() = runTest {
        pending(dogs, "p1", 3, 4, 120)
        f.accounts.accounts[AccountId("old")] = f.account(AccountId("old"), "Vieja", archived = true)
        suspend fun go(person: PersonId = mom, account: AccountId = f.bbva, minor: Long = 50, on: LocalDate = f.today) =
            receive(person, account, pen(minor), AllocationStrategy.OLDEST_FIRST, null, emptyList(), null, on)

        assertEquals(failed(BusinessError.NotFound), go(person = PersonId("ghost")))
        assertEquals(failed(BusinessError.NotFound), go(account = AccountId("ghost")))
        assertEquals(failed(BusinessError.AlreadyArchived), go(account = AccountId("old")))
        assertEquals(failed(BusinessError.FutureDate), go(on = f.today.plusDays(1)))
        assertEquals(failed(BusinessError.InvalidAmount), go(minor = 0))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun voidingAReimbursementWarnsWhenTheBalanceWouldGoNegativeAndEmitsMovementVoided() = runTest {
        val reimbursement = Movement(
            MovementId("mr1"), MovementType.REIMBURSEMENT, f.bbva, null, pen(1_500), null, null, f.today, MovementStatus.ACTIVE, 1, emptyList(),
        )
        f.movements.movements[reimbursement.id] = reimbursement
        val void = VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock)

        assertEquals(failed(BusinessError.WouldGoNegative(-500)), void(reimbursement.id, "error"))
        assertEquals(Outcome.Ok(Unit), void(reimbursement.id, "error", confirmNegativeBalance = true))
        assertEquals(MovementVoided(reimbursement.id, "error", null), f.store.events.single().event)
    }

    @Test
    fun waitsForTheWriteLockSoTwoCallsNeverComputeOnTheSamePendingState() = runTest {
        pending(dogs, "p1", 3, 4, 120)
        val release = CompletableDeferred<Unit>()
        val holder = launch { f.writeLock.withLock { release.await() } }
        runCurrent()

        val call = async { receive(mom, f.bbva, pen(120), AllocationStrategy.OLDEST_FIRST, null, emptyList(), null, f.today) }
        runCurrent()
        assertFalse(call.isCompleted)
        assertEquals(0, f.store.events.size)

        release.complete(Unit)
        assertTrue(call.await() is Outcome.Ok<*>)
        holder.join()
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun aManualAllocationInAnotherCurrencyIsRejected() = runTest {
        pending(dogs, "p1", 3, 4, 120)
        val foreign = AllocationData(PartId("p1"), Money(120, CurrencyCode.USD))

        val result = receive(mom, f.bbva, pen(120), AllocationStrategy.MANUAL, null, listOf(foreign), null, f.today)

        assertEquals(failed(BusinessError.UnsupportedCurrency), result)
        assertEquals(0, f.store.events.size)
    }
}
