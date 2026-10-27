package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.usecase.ArchiveGuard
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.MovementCorrectionGuard
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.people.domain.usecase.ArchivePersonUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MovementGuardsTest {
    private val f = MovementFixture()
    private val m1 = MovementId("m1")

    private fun draft() = MovementDraft.Expense(f.bbva, f.pen(300), listOf(f.input(300, partId = PartId("p1"))), "nota", f.today)

    @Test
    fun aVoidGuardThatRejectsStopsTheVoidAndNothingIsRecorded() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)
        val seen = mutableListOf<MovementId>()
        val guard = MovementVoidGuard { id -> seen += id; BusinessError.BalanceNotZero }
        val void = VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock, setOf(guard))

        assertEquals(Outcome.Failed(BusinessError.BalanceNotZero), void(m1, "duplicado"))
        assertEquals(listOf(m1), seen)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun voidGuardsThatAcceptLetTheVoidThrough() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100)
        val void = VoidMovementUseCase(f.movements, f.accounts, f.rules, f.recorder, f.writeLock, setOf(MovementVoidGuard { null }))

        assertEquals(Outcome.Ok(Unit), void(m1, "duplicado"))
        assertEquals(1, f.store.events.size)
    }

    @Test
    fun aCorrectionGuardSeesTheIdAndTheResultingSnapshot() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 400)
        val seen = mutableListOf<Pair<MovementId, MovementSnapshot>>()
        val guard = MovementCorrectionGuard { id, snapshot -> seen += id to snapshot; BusinessError.InvalidPart }
        val correct = CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, setOf(guard))

        assertEquals(Outcome.Failed(BusinessError.InvalidPart), correct(m1, draft(), "monto mal"))
        assertEquals(1, seen.size)
        assertEquals(m1, seen.single().first)
        assertEquals(f.pen(300), (seen.single().second as MovementSnapshot.Expense).total)
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun anArchiveGuardStopsArchivingAPerson() = runTest {
        val people = FakePersonRepository()
        val p1 = PersonId("p1")
        people.people[p1] = Person(p1, "Mamá", false)
        val recorder = EventRecorder(f.store, f.ids, f.clock)
        val guard = ArchiveGuard { BusinessError.BalanceNotZero }

        assertEquals(Outcome.Failed(BusinessError.BalanceNotZero), ArchivePersonUseCase(people, recorder, setOf(guard))(p1))
        assertEquals(0, f.store.events.size)
        assertEquals(Outcome.Ok(Unit), ArchivePersonUseCase(people, recorder, emptySet())(p1))
    }
}
