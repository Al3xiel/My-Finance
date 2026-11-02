package com.al3xiel.my_finances

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.ProjectionRebuilderImpl
import com.al3xiel.my_finances.data.projection.ProjectionVersionsImpl
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.ProjectionVersionGuard
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.reconciliation.data.ReconciliationRepositoryImpl
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.LinkMovementUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.RecordBalanceCheckUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.ResolveReconciliationUseCase
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import kotlinx.coroutines.flow.first
import java.time.Clock

/**
 * Los casos de uso reales de la conciliación sobre Room y el registro de eventos reales. `wire` se puede repetir con otro
 * conjunto de proyectores: así una prueba imita que la app se reinicia con una versión nueva del código.
 */
class ReconciliationWorld(private val database: AppDatabase, private val clock: Clock, private val ids: IdGenerator) {
    lateinit var store: EventStoreImpl
    lateinit var accounts: AccountRepositoryImpl
    lateinit var reconciliations: ReconciliationRepositoryImpl
    lateinit var createAccount: CreateAccountUseCase
    lateinit var createIncomeSource: CreateIncomeSourceUseCase
    lateinit var registerIncome: RegisterIncomeUseCase
    lateinit var correct: CorrectMovementUseCase
    lateinit var void: VoidMovementUseCase
    lateinit var record: RecordBalanceCheckUseCase
    lateinit var link: LinkMovementUseCase
    lateinit var resolve: ResolveReconciliationUseCase
    lateinit var replay: ReplayProjectionsUseCase
    lateinit var versionGuard: ProjectionVersionGuard

    /** Los de la app, desordenados a propósito: el orden lo pone `EventProjector.order`. */
    fun standardProjectors(): Set<EventProjector> = setOf(
        ReconciliationProjector(database.reconciliationDao(), LedgerPoster(database.movementDao(), database.ledgerDao())),
        MovementProjector(database.movementDao(), database.ledgerDao()),
        CatalogProjector(database.catalogDao()),
    )

    fun wire(projectors: Set<EventProjector>) {
        store = EventStoreImpl(database, projectors, ids, clock, "1.0")
        accounts = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())
        reconciliations = ReconciliationRepositoryImpl(database.reconciliationQueryDao())
        val categories = CategoryRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val tags = TagRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val people = PersonRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val sources = IncomeSourceRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val movements = MovementRepositoryImpl(database.movementDao(), database.movementQueryDao(), store)

        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val lock = MovementWriteLock()
        val linkGuards = setOf(ReconciliationGuards(reconciliations))
        createAccount = CreateAccountUseCase(accounts, recorder, ids)
        createIncomeSource = CreateIncomeSourceUseCase(sources, people, recorder, ids)
        registerIncome = RegisterIncomeUseCase(rules, recorder, ids, linkGuards)
        correct = CorrectMovementUseCase(rules, accounts, movements, recorder, ids, lock, emptySet(), emptySet(), emptySet(), linkGuards)
        void = VoidMovementUseCase(movements, accounts, rules, recorder, lock)
        record = RecordBalanceCheckUseCase(accounts, reconciliations, recorder, ids)
        link = LinkMovementUseCase(reconciliations, movements, recorder)
        resolve = ResolveReconciliationUseCase(reconciliations, recorder, ids, lock)
        val rebuilder = ProjectionRebuilderImpl(database, projectors, store, clock)
        replay = ReplayProjectionsUseCase(store, rebuilder, ids, clock)
        versionGuard = ProjectionVersionGuard(ProjectionVersionsImpl(projectors, database.projectionStateDao()), store, replay)
    }

    fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    suspend fun openAccount(name: String, openingMinor: Long): AccountId {
        createAccount(name, com.al3xiel.my_finances.domain.model.event.AccountType.BANK, null, pen(openingMinor)).value()
        return accounts.observeAccounts().first().first { it.account.name == name }.account.id
    }

    suspend fun accountId(name: String): AccountId = accounts.observeAccounts().first().first { it.account.name == name }.account.id

    suspend fun balance(name: String): Long = accounts.observeAccounts().first().first { it.account.name == name }.balanceMinor

    suspend fun source(name: String): IncomeSourceId = createIncomeSource(name, null).value()

    suspend fun difference(id: ReconciliationId): Money? = reconciliations.remainingDifference(id)

    suspend fun movementExists(id: MovementId): Boolean = database.movementDao().movement(id.value) != null
}
