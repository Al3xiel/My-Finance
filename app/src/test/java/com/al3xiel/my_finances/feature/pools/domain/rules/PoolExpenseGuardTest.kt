package com.al3xiel.my_finances.feature.pools.domain.rules

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.model.ReimbursedPart
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoolExpenseGuardTest {
    private val pools = FakePoolRepository()
    private val guard = PoolExpenseGuard(pools)

    private val m1 = MovementId("m1")
    private val dogs = PoolId("dogs")
    private val cats = PoolId("cats")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun pool(id: PoolId, archived: Boolean = false) {
        pools.pools[id] = Pool(id, id.value, PersonId("mom"), PoolKind.PERMANENT, null, emptyList(), archived)
    }

    private fun part(id: String, minor: Long, pool: PoolId?) =
        ExpensePartData(PartId(id), pen(minor), CategoryId("pets"), emptyList(), pool, null)

    private fun snapshot(vararg parts: ExpensePartData) =
        MovementSnapshot.Expense(AccountId("a"), pen(parts.sumOf { it.amount.amountMinor }), parts.toList(), null)

    private fun reimbursed(partId: String, pool: PoolId, minor: Long) {
        pools.reimbursed[m1] = pools.reimbursed[m1].orEmpty() + ReimbursedPart(PartId(partId), pool, pen(minor))
    }

    @Test
    fun anExpenseWithReimbursementsCannotBeVoided() = runTest {
        reimbursed("p1", dogs, 400)

        assertEquals(BusinessError.HasReimbursements, guard.check(m1))
        assertNull(guard.check(MovementId("other")))
    }

    @Test
    fun aReimbursedPartMustStayInItsPoolAndNotDropBelowWhatWasReimbursed() = runTest {
        reimbursed("p1", dogs, 400)

        assertNull(guard.check(m1, snapshot(part("p1", 400, dogs), part("p2", 50, null))))
        assertNull(guard.check(m1, snapshot(part("p1", 900, dogs))))
        assertEquals(BusinessError.BelowReimbursedAmount, guard.check(m1, snapshot(part("p1", 399, dogs))))
        assertEquals(BusinessError.ReimbursedPartLocked, guard.check(m1, snapshot(part("p1", 400, cats))))
        assertEquals(BusinessError.ReimbursedPartLocked, guard.check(m1, snapshot(part("p1", 400, null))))
        assertEquals(BusinessError.ReimbursedPartLocked, guard.check(m1, snapshot(part("p2", 400, dogs))))
    }

    @Test
    fun partsWithoutReimbursementsAreFreeAndIncomeIsIgnored() = runTest {
        reimbursed("p1", dogs, 400)

        assertNull(guard.check(m1, snapshot(part("p1", 400, dogs), part("p2", 10, cats))))
        assertNull(guard.check(m1, MovementSnapshot.Income(AccountId("a"), pen(1), com.al3xiel.my_finances.domain.model.IncomeSourceId("s"), null)))
        assertNull(guard.check(MovementId("other"), snapshot(part("x", 10, null))))
    }

    @Test
    fun aPoolReferenceMustExistAndBeActiveUnlessThePartAlreadyHadIt() = runTest {
        pool(dogs); pool(cats, archived = true)

        assertNull(guard.check(dogs, unchanged = false))
        assertEquals(BusinessError.NotFound, guard.check(PoolId("ghost"), unchanged = false))
        assertEquals(BusinessError.AlreadyArchived, guard.check(cats, unchanged = false))
        assertNull(guard.check(cats, unchanged = true))
    }
}
