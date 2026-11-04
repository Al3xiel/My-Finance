package com.al3xiel.my_finances.feature.movements.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.pool.PoolEntity
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.ReimbursementStatus
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.ExpensePartData
import com.al3xiel.my_finances.domain.model.event.ExpenseRegistered
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.MovementCorrected
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.model.event.MovementVoided
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.model.event.TransferRegistered
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.model.HistoryKind
import com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class MovementQueriesTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var movements: MovementRepositoryImpl
    private lateinit var accounts: AccountRepositoryImpl

    private val bbva = AccountId("bbva")
    private val cash = AccountId("cash")
    private val food = CategoryId("food")
    private val home = CategoryId("home")
    private val tag = TagId("t1")

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun day(d: Int) = LocalDate.of(2026, 10, d)

    private fun part(id: String, minor: Long, category: CategoryId, tags: List<TagId> = emptyList()) =
        ExpensePartData(PartId(id), pen(minor), category, tags, null, null)

    private suspend fun append(on: LocalDate, event: DomainEvent) {
        store.append("c", listOf(NewEvent(event, on)))
    }

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        val projectors = setOf(
            CatalogProjector(database.catalogDao()),
            MovementProjector(database.movementDao(), database.ledgerDao()),
        )
        val ids = object : IdGenerator {
            private var n = 0
            override fun newId() = "e${n++}"
        }
        store = EventStoreImpl(database, projectors, ids, Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC), "1.0")
        movements = MovementRepositoryImpl(database.movementDao(), database.movementQueryDao(), store)
        accounts = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())

        append(day(1), AccountCreated(bbva, "BBVA", AccountType.BANK, CurrencyCode.PEN, null, pen(10_000)))
        append(day(1), AccountCreated(cash, "Efectivo", AccountType.CASH, CurrencyCode.PEN, null, pen(0)))
        append(day(1), CategoryCreated(food, "Comida", "restaurant", "#FF0000", null))
        append(day(1), CategoryCreated(home, "Hogar", "home", "#00FF00", null))
        append(day(1), TagCreated(tag, "Viaje", TagKind.TOPIC, null))
        append(
            day(5),
            ExpenseRegistered(
                MovementId("m1"), bbva, pen(3_000), listOf(part("p1", 2_000, food, listOf(tag)), part("p2", 1_000, home)), null, "Mercado 100%",
            ),
        )
        append(day(10), IncomeRegistered(MovementId("m2"), bbva, pen(5_000), IncomeSourceId("s1"), null, "Sueldo"))
        append(day(12), TransferRegistered(MovementId("m3"), bbva, cash, pen(1_000), FeeData(pen(50), home), null, "retiro"))
        append(day(15), ExpenseRegistered(MovementId("m4"), cash, pen(400), listOf(part("p3", 400, food)), null, null))
    }

    @After
    fun tearDown() = database.close()

    private fun ids(filter: MovementFilter) = runBlocking { movements.observeMovements(filter).first().map { it.id.value } }

    private fun linkToPool(partId: String, minor: Long, category: CategoryId) = runBlocking {
        database.poolDao().upsertPool(PoolEntity("dogs", "Perros", "mom", PoolKind.PERMANENT, null, "", false))
        database.movementDao().updatePartCore(partId, minor, category.value, "dogs", null, null)
    }

    @Test
    fun aPartWithAPoolCarriesTheReimbursedAndPendingAmounts() = runBlocking {
        linkToPool("p1", 2_000, food)
        linkToPool("p2", 1_000, home)
        linkToPool("p3", 400, food)
        database.reimbursementDao().setPartReimbursement("p2", 400, ReimbursementStatus.PARTIALLY_REIMBURSED)
        database.reimbursementDao().setPartReimbursement("p3", 400, ReimbursementStatus.REIMBURSED)

        val m1 = movements.observeDetail(MovementId("m1")).first()!!.reimbursements
        assertEquals(listOf("p1", "p2"), m1.map { it.partId.value })
        assertEquals(listOf(ReimbursementStatus.PENDING, ReimbursementStatus.PARTIALLY_REIMBURSED), m1.map { it.status })
        assertEquals(listOf(2_000L, 600L), m1.map { it.pending.amountMinor })
        assertEquals("Perros", m1.first().poolName)
        assertEquals(listOf(ReimbursementStatus.REIMBURSED), movements.observeDetail(MovementId("m4")).first()!!.reimbursements.map { it.status })
    }

    @Test
    fun aVoidedMovementShowsNoReimbursementState() = runBlocking {
        linkToPool("p1", 2_000, food)
        assertEquals(1, movements.observeDetail(MovementId("m1")).first()!!.reimbursements.size)

        append(day(20), MovementVoided(MovementId("m1"), "duplicado", null))

        assertEquals(emptyList<Any>(), movements.observeDetail(MovementId("m1")).first()!!.reimbursements)
    }

    @Test
    fun aPartDetailComesBackWithItsPart() = runBlocking {
        database.movementDao().updatePartCore("p1", 2_000, food.value, null, null, "Pastillas de Boby")

        val parts = movements.find(MovementId("m1"))!!.parts

        assertEquals(listOf<String?>("Pastillas de Boby", null), parts.map { it.note })
    }

    @Test
    fun aMovementWithoutPoolPartsHasNoReimbursements() = runBlocking {
        assertEquals(emptyList<Any>(), movements.observeDetail(MovementId("m2")).first()!!.reimbursements)
    }

    @Test
    fun listsNewestFirst() {
        assertEquals(listOf("m4", "m3", "m2", "m1"), ids(MovementFilter()))
    }

    @Test
    fun filtersByAccountIncludingTheCounterAccountOfTransfers() {
        assertEquals(listOf("m4", "m3"), ids(MovementFilter(accountId = cash)))
    }

    @Test
    fun filtersByTypeCategoryTagAndDates() {
        assertEquals(listOf("m3"), ids(MovementFilter(type = MovementType.TRANSFER)))
        assertEquals(listOf("m3", "m1"), ids(MovementFilter(categoryId = home)))
        assertEquals(listOf("m1"), ids(MovementFilter(tagId = tag)))
        assertEquals(listOf("m3", "m2"), ids(MovementFilter(from = day(10), to = day(12))))
    }

    @Test
    fun searchesNotesIgnoringCaseAndTreatingWildcardsAsLiterals() {
        assertEquals(listOf("m1"), ids(MovementFilter(text = "mercado")))
        assertEquals(listOf("m1"), ids(MovementFilter(text = "100%")))
        assertEquals(listOf("m1"), ids(MovementFilter(text = "%")))
        assertEquals(emptyList<String>(), ids(MovementFilter(text = "_")))
    }

    @Test
    fun aCategorySetMatchesAPartInAnyOfItsMembers() {
        assertEquals(listOf("m4", "m3", "m1"), ids(MovementFilter(categoryId = food, categoryIds = setOf(food, home))))
        assertEquals(listOf("m4"), ids(MovementFilter(accountId = cash, categoryId = food, categoryIds = setOf(food))))
        assertEquals(listOf("m4", "m1"), ids(MovementFilter(categoryId = food)))
    }

    @Test
    fun combinesFiltersWithAnd() {
        assertEquals(listOf("m4"), ids(MovementFilter(accountId = cash, categoryId = food)))
    }

    @Test
    fun detailHasPartsTagsAndEventHistory() = runBlocking {
        append(day(6), MovementCorrected(MovementId("m1"), MovementSnapshot.Expense(bbva, pen(2_000), listOf(part("p1", 2_000, food, listOf(tag))), "Mercado"), "monto mal", null))
        append(day(7), MovementVoided(MovementId("m1"), "duplicado", null))

        val detail = movements.observeDetail(MovementId("m1")).first()!!

        assertEquals(1, detail.movement.parts.size)
        assertEquals(listOf(tag), detail.movement.parts.single().tagIds)
        assertEquals(listOf(HistoryKind.REGISTERED, HistoryKind.CORRECTED, HistoryKind.VOIDED), detail.history.map { it.kind })
        assertEquals(listOf(null, "monto mal", "duplicado"), detail.history.map { it.reason })
        assertEquals(2, movements.find(MovementId("m1"))!!.revision)
    }

    @Test
    fun accountsHaveBalancesBalanceAtADateAndALedger() = runBlocking {
        val list = accounts.observeAccounts().first()
        assertEquals(mapOf("BBVA" to 10_950L, "Efectivo" to 600L), list.associate { it.account.name to it.balanceMinor })

        assertEquals(7_000L, accounts.balanceAt(bbva, day(9)))
        assertEquals(10_950L, accounts.balanceAt(bbva, day(30)))
        assertEquals(600L, accounts.balanceMinor(cash))

        val ledger = accounts.observeLedger(bbva).first()
        assertEquals(10_950L, ledger.sumOf { it.deltaMinor })
        assertEquals(day(12), ledger.first().occurredOn)
    }

    @Test
    fun lastExpenseIsTheMostRecentActiveExpenseAndIgnoresLaterIncome() = runBlocking {
        append(day(16), IncomeRegistered(MovementId("m5"), bbva, pen(100), IncomeSourceId("s1"), null, null))

        assertEquals(MovementId("m4"), movements.lastExpense()?.id)
    }

    @Test
    fun lastExpenseSkipsAVoidedOneAndIsNullWithoutExpenses() = runBlocking {
        append(day(16), MovementVoided(MovementId("m4"), "duplicado", null))
        assertEquals(MovementId("m1"), movements.lastExpense()?.id)

        append(day(17), MovementVoided(MovementId("m1"), "duplicado", null))
        assertEquals(null, movements.lastExpense())
    }
}
