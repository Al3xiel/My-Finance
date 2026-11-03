package com.al3xiel.my_finances.feature.quickentry.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.FakePersonRepository
import com.al3xiel.my_finances.feature.funds.domain.model.FundStatus
import com.al3xiel.my_finances.feature.funds.domain.usecase.FakeFundRepository
import com.al3xiel.my_finances.feature.movements.domain.usecase.MovementFixture
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.people.domain.model.Person
import com.al3xiel.my_finances.feature.pools.domain.model.Pool
import com.al3xiel.my_finances.feature.pools.domain.usecase.FakePoolRepository
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import com.al3xiel.my_finances.feature.quickentry.domain.model.QuickEntryDraft
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryUseCasesTest {
    private val f = MovementFixture()
    private val pools = FakePoolRepository()
    private val funds = FakeFundRepository()
    private val people = FakePersonRepository()
    private val mom = PersonId("mom")
    private val dogs = PoolId("dogs")

    private fun pool(archived: Boolean = false, category: com.al3xiel.my_finances.domain.model.CategoryId? = null) =
        Pool(dogs, "Perros", mom, PoolKind.PERMANENT, category, listOf(f.tag), archived).also { pools.pools[dogs] = it }

    private val snapshot = LoadCatalogSnapshotUseCase(f.accounts, f.categories, f.tags, pools, people, funds, f.sources)
    private val suggest = SuggestDefaultsUseCase(f.movements, f.accounts, f.categories, pools)
    private val confirm = ConfirmQuickEntryUseCase(
        RegisterExpenseUseCase(f.rules, f.accounts, f.recorder, f.ids, f.writeLock),
        RegisterIncomeUseCase(f.rules, f.recorder, f.ids),
        pools,
        f.clock,
    )

    // --- LoadCatalogSnapshotUseCase ---

    @Test fun `snapshot has only active entities and the pool answers to its responsible`() = runTest {
        people.people[mom] = Person(mom, "Mamá", false)
        pool()
        f.accounts.accounts[com.al3xiel.my_finances.domain.model.AccountId("old")] = f.account(com.al3xiel.my_finances.domain.model.AccountId("old"), "Viejo", archived = true)
        funds.put(FundId("open"), name = "Pasajes")
        funds.put(FundId("shut"), name = "Cerrado", status = FundStatus.CLOSED)

        val entries = snapshot().entries
        assertFalse(entries.any { it.id == "old" || it.id == "shut" })
        assertEquals(listOf("Perros", "Mamá"), entries.single { it.kind == EntityKind.POOL }.names)
        assertEquals(setOf("bbva", "cash"), entries.filter { it.kind == EntityKind.ACCOUNT }.map { it.id }.toSet())
        assertTrue(entries.any { it.kind == EntityKind.FUND && it.id == "open" })
        assertTrue(entries.any { it.kind == EntityKind.INCOME_SOURCE && it.id == "s1" })
    }

    // --- SuggestDefaultsUseCase ---

    @Test fun `without history it proposes the first active account and no category`() = runTest {
        val s = suggest(null)
        assertEquals(f.bbva, s.accountId)
        assertNull(s.categoryId)
    }

    @Test fun `last expense gives account and category`() = runTest {
        f.expenseMovement("m1", f.cash, "p1" to 100L)
        val s = suggest(null)
        assertEquals(f.cash, s.accountId)
        assertEquals(f.food, s.categoryId)
    }

    @Test fun `archived account of the last expense falls back to the first active`() = runTest {
        f.expenseMovement("m1", f.cash, "p1" to 100L)
        f.accounts.accounts[f.cash] = f.account(f.cash, "Efectivo", archived = true)
        assertEquals(f.bbva, suggest(null).accountId)
    }

    @Test fun `pool default category and tags win over the last expense`() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100L)
        pool(category = f.fees)
        val s = suggest(dogs)
        assertEquals(f.fees, s.categoryId)
        assertEquals(setOf(f.tag), s.tagIds)
    }

    @Test fun `pool without category keeps the last expense category`() = runTest {
        f.expenseMovement("m1", f.bbva, "p1" to 100L)
        pool()
        assertEquals(f.food, suggest(dogs).categoryId)
    }

    // --- ConfirmQuickEntryUseCase ---

    @Test fun `expense is registered today with the editor use case`() = runTest {
        val result = confirm(QuickEntryDraft.Expense(f.bbva, f.pen(300), f.food, emptySet(), null, null, "huevos"))
        assertEquals(Outcome.Ok(MovementId("id-1")), result)
        val stored = f.store.events.single()
        assertEquals(f.today, stored.occurredOn)
        val event = stored.event as ExpenseRegistered
        assertEquals(f.pen(300), event.total)
        assertEquals("huevos", event.note)
        assertEquals(1, event.parts.size)
    }

    @Test fun `pool tags are applied like in the editor`() = runTest {
        pool()
        confirm(QuickEntryDraft.Expense(f.bbva, f.pen(100), f.food, emptySet(), dogs, null, null))
        val event = f.store.events.single().event as ExpenseRegistered
        assertEquals(listOf(f.tag), event.parts.single().tagIds)
        assertEquals(dogs, event.parts.single().poolId)
    }

    @Test fun `income is registered today`() = runTest {
        val result = confirm(QuickEntryDraft.Income(f.bbva, f.pen(5000), f.source, null))
        assertTrue(result is Outcome.Ok)
        val stored = f.store.events.single()
        assertEquals(f.today, stored.occurredOn)
        assertEquals(f.pen(5000), (stored.event as IncomeRegistered).amount)
    }

    @Test fun `negative balance warns and the confirmed retry saves`() = runTest {
        val draft = QuickEntryDraft.Expense(f.bbva, f.pen(2000), f.food, emptySet(), null, null, null)
        val warned = confirm(draft)
        assertTrue((warned as Outcome.Failed).error is BusinessError.WouldGoNegative)
        assertTrue(f.store.events.isEmpty())
        assertTrue(confirm(draft, confirmNegativeBalance = true) is Outcome.Ok)
    }

    @Test fun `use case validation is propagated`() = runTest {
        val result = confirm(QuickEntryDraft.Expense(f.bbva, f.pen(0), f.food, emptySet(), null, null, null))
        assertTrue(result is Outcome.Failed)
        assertTrue(f.store.events.isEmpty())
    }

    @Test fun `missing pool fails with not found`() = runTest {
        val result = confirm(QuickEntryDraft.Expense(f.bbva, f.pen(100), f.food, emptySet(), PoolId("ghost"), null, null))
        assertEquals(Outcome.Failed(BusinessError.NotFound), result)
    }
}
