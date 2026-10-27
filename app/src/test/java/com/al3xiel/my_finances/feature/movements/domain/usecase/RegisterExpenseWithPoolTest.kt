package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolExpenseGuard
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RegisterExpenseWithPoolTest {
    private val f = MovementFixture()
    private val pools = FakePoolRepository()
    private val guard = PoolExpenseGuard(pools)
    private val register = RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids, setOf(guard))
    private val correct = CorrectMovementUseCase(f.rules, f.accounts, f.movements, f.recorder, f.ids, f.writeLock, emptySet(), setOf(guard))

    private val dogs = PoolId("dogs")
    private val cats = PoolId("cats")

    init {
        pools.pools[dogs] = Pool(dogs, "Perros", PersonId("mom"), PoolKind.PERMANENT, null, emptyList(), false)
        pools.pools[cats] = Pool(cats, "Gatos", PersonId("mom"), PoolKind.PERMANENT, null, emptyList(), true)
    }

    private fun input(minor: Long, pool: PoolId?, partId: PartId? = null) =
        ExpensePartInput(partId, f.pen(minor), f.food, emptyList(), pool)

    @Test
    fun theRegisteredPartCarriesItsPool() = runTest {
        val result = register(f.bbva, f.pen(150), listOf(input(100, dogs), input(50, null)), null, f.today)

        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        val parts = (f.store.events.single().event as ExpenseRegistered).parts
        assertEquals(listOf(dogs, null), parts.map { it.poolId })
        assertEquals(listOf(null, null), parts.map { it.fundId })
    }

    @Test
    fun unknownOrArchivedPoolsAreRejectedWithoutRecordingAnything() = runTest {
        assertEquals(Outcome.Failed(BusinessError.NotFound), register(f.bbva, f.pen(100), listOf(input(100, PoolId("ghost"))), null, f.today))
        assertEquals(Outcome.Failed(BusinessError.AlreadyArchived), register(f.bbva, f.pen(100), listOf(input(100, cats)), null, f.today))
        assertEquals(0, f.store.events.size)
    }

    @Test
    fun withoutPoolsNothingChangesForExistingCallers() = runTest {
        val plain = RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids)

        assertEquals(Outcome.Ok(MovementId("id-1")), plain(f.bbva, f.pen(100), listOf(input(100, null)), null, f.today))
    }

    /** Un gasto ya guardado cuyas partes están en `pool`. */
    private fun storedExpense(pool: PoolId) {
        val movement = f.expenseMovement("m1", f.bbva, "p1" to 400)
        f.movements.movements[movement.id] = movement.copy(parts = movement.parts.map { it.copy(poolId = pool) })
    }

    @Test
    fun correctionKeepsThePoolOfThePartsThatStay() = runTest {
        storedExpense(dogs)

        val draft = MovementDraft.Expense(f.bbva, f.pen(300), listOf(input(300, dogs, PartId("p1"))), "nota", f.today)
        assertEquals(Outcome.Ok(Unit), correct(MovementId("m1"), draft, "monto mal"))

        val snapshot = (f.store.events.single().event as MovementCorrected).snapshot as MovementSnapshot.Expense
        assertEquals(dogs, snapshot.parts.single().poolId)
    }

    @Test
    fun correctionAcceptsAnArchivedPoolOnThePartThatAlreadyHadItButNotOnOthers() = runTest {
        storedExpense(cats)

        val keep = MovementDraft.Expense(f.bbva, f.pen(400), listOf(input(400, cats, PartId("p1"))), "nota", f.today)
        assertEquals(Outcome.Ok(Unit), correct(MovementId("m1"), keep, "nota"))

        val addNew = MovementDraft.Expense(f.bbva, f.pen(450), listOf(input(400, cats, PartId("p1")), input(50, cats)), "nota", f.today)
        assertEquals(Outcome.Failed(BusinessError.AlreadyArchived), correct(MovementId("m1"), addNew, "otra"))
    }

    @Test
    fun anArchivedPoolIsToleratedOnAPartOnlyWhileItsAmountDoesNotGrow() = runTest {
        storedExpense(cats)

        val grows = MovementDraft.Expense(f.bbva, f.pen(500), listOf(input(500, cats, PartId("p1"))), "nota", f.today)
        assertEquals(Outcome.Failed(BusinessError.AlreadyArchived), correct(MovementId("m1"), grows, "monto mal"))
        assertEquals(0, f.store.events.size)

        val shrinks = MovementDraft.Expense(f.bbva, f.pen(300), listOf(input(300, cats, PartId("p1"))), "nota", f.today)
        assertEquals(Outcome.Ok(Unit), correct(MovementId("m1"), shrinks, "monto mal"))
    }
}
