package com.al3xiel.my_finances.feature.movements.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.MovementStatus
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.domain.model.PartKind
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.accounts.domain.FakeAccountRepository
import com.al3xiel.my_finances.feature.accounts.domain.FakeIncomeSourceRepository
import com.al3xiel.my_finances.feature.accounts.domain.model.Account
import com.al3xiel.my_finances.feature.accounts.domain.model.IncomeSource
import com.al3xiel.my_finances.feature.accounts.domain.FakeCategoryRepository
import com.al3xiel.my_finances.feature.categories.domain.model.Category
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.Movement
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDetail
import com.al3xiel.my_finances.feature.movements.domain.model.MovementFilter
import com.al3xiel.my_finances.feature.movements.domain.model.MovementPart
import com.al3xiel.my_finances.feature.movements.domain.model.MovementSummary
import com.al3xiel.my_finances.feature.movements.domain.repository.MovementRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.accounts.domain.FakeTagRepository
import com.al3xiel.my_finances.feature.tags.domain.model.Tag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class FakeMovementRepository : MovementRepository {
    val movements = mutableMapOf<MovementId, Movement>()

    /** Para simular un fallo técnico y una llamada que se queda esperando (doble toque). */
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun find(id: MovementId): Movement? {
        gate?.await()
        failure?.let { throw it }
        return movements[id]
    }
    override suspend fun lastExpense() =
        movements.values.lastOrNull { it.type == MovementType.EXPENSE && it.status == MovementStatus.ACTIVE }

    /** Lo que devuelve la lista y los filtros con los que se pidió, en orden. */
    var summaries: List<MovementSummary> = emptyList()
    val requestedFilters = mutableListOf<MovementFilter>()
    val details = mutableMapOf<MovementId, MovementDetail>()

    override fun observeMovements(filter: MovementFilter): Flow<List<MovementSummary>> = flow {
        requestedFilters += filter
        emit(summaries)
    }

    override fun observeDetail(id: MovementId): Flow<MovementDetail?> = flow { emit(details[id]) }
}

/** Hoy es 2026-10-26; BBVA tiene S/ 10.00 y Efectivo S/ 0.00; categorías, concepto y fuente ya existen. */
class MovementFixture {
    val store = FakeEventStore()
    val ids = SequentialIdGenerator()
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC)
    val recorder = EventRecorder(store, ids, clock)

    val accounts = FakeAccountRepository()
    val categories = FakeCategoryRepository()
    val tags = FakeTagRepository()
    val sources = FakeIncomeSourceRepository()
    val movements = FakeMovementRepository()
    val rules = MovementRules(accounts, categories, tags, sources, clock)
    val writeLock = MovementWriteLock()

    val today: LocalDate = LocalDate.of(2026, 10, 26)
    val bbva = AccountId("bbva")
    val cash = AccountId("cash")
    val food = CategoryId("food")
    val fees = CategoryId("fees")
    val tag = TagId("t1")
    val source = IncomeSourceId("s1")

    init {
        accounts.accounts[bbva] = account(bbva, "BBVA")
        accounts.accounts[cash] = account(cash, "Efectivo")
        accounts.balances[bbva] = 1_000
        categories.categories[food] = Category(food, "Comida", null, false)
        categories.categories[fees] = Category(fees, "Comisiones", null, false)
        tags.tags[tag] = Tag(tag, "Viaje", TagKind.TOPIC, null, false)
        sources.sources[source] = IncomeSource(source, "Sueldo", IncomeSourceKind.REGULAR, null)
    }

    fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    fun account(id: AccountId, name: String, archived: Boolean = false) =
        Account(id, name, AccountType.BANK, CurrencyCode.PEN, null, archived)

    fun input(minor: Long, partId: PartId? = null, category: CategoryId = food, tags: List<TagId> = emptyList()) =
        ExpensePartInput(partId, pen(minor), category, tags)

    fun expenseMovement(id: String, account: AccountId, vararg parts: Pair<String, Long>, status: MovementStatus = MovementStatus.ACTIVE) =
        Movement(
            MovementId(id), MovementType.EXPENSE, account, null, pen(parts.sumOf { it.second }), null, null, today, status, 1,
            parts.map { (partId, minor) -> MovementPart(PartId(partId), pen(minor), food, PartKind.NORMAL, emptyList()) },
        ).also { movements.movements[it.id] = it }

    fun incomeMovement(id: String, account: AccountId, minor: Long, status: MovementStatus = MovementStatus.ACTIVE) =
        Movement(MovementId(id), MovementType.INCOME, account, null, pen(minor), source, null, today, status, 1, emptyList())
            .also { movements.movements[it.id] = it }

    fun transferMovement(id: String) =
        Movement(MovementId(id), MovementType.TRANSFER, bbva, cash, pen(100), null, null, today, MovementStatus.ACTIVE, 1, emptyList())
            .also { movements.movements[it.id] = it }
}
