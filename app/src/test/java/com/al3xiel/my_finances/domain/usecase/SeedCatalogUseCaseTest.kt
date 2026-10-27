package com.al3xiel.my_finances.domain.usecase

import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SeedCatalogUseCaseTest {
    private val store = FakeEventStore()
    private val clock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC)
    private val seed = SeedCatalogUseCase(EventRecorder(store, SequentialIdGenerator(), clock), store)

    @Test
    fun firstRunCreatesSystemSourcesAndDefaultCategoriesWithFixedIds() = runTest {
        seed()

        val sources = store.events.map { it.event }.filterIsInstance<IncomeSourceCreated>()
        val categories = store.events.map { it.event }.filterIsInstance<CategoryCreated>()
        assertEquals(SystemCatalog.incomeSources.map { it.id }, sources.map { it.incomeSourceId })
        assertEquals(SystemCatalog.defaultCategories.map { it.id }, categories.map { it.categoryId })
        assertEquals(
            setOf(
                IncomeSourceKind.OPENING_BALANCE,
                IncomeSourceKind.UNKNOWN_ADJUSTMENT,
                IncomeSourceKind.REIMBURSEMENT,
                IncomeSourceKind.LOAN_REPAYMENT,
            ),
            SystemCatalog.incomeSources.map { it.kind }.toSet(),
        )
        assertTrue(categories.any { it.name == "Comisiones" && it.categoryId == SystemCatalog.feeCategoryId })
        assertTrue(store.events.all { it.occurredOn == LocalDate.of(2026, 10, 26) })
    }

    @Test
    fun isIdempotent() = runTest {
        seed()
        val count = store.events.size
        seed()
        assertEquals(count, store.events.size)
    }

    @Test
    fun onlyMissingItemsAreCreated() = runTest {
        seed()
        val keep = store.events.take(3).toList()
        store.events.clear()
        store.events += keep
        seed()
        assertEquals(SystemCatalog.incomeSources.size + SystemCatalog.defaultCategories.size, store.events.size)
    }
}
