package com.al3xiel.my_finances

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.ProjectionRebuilderImpl
import com.al3xiel.my_finances.data.projection.catalog.CatalogProjector
import com.al3xiel.my_finances.data.projection.loan.LoanProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodRefresher
import com.al3xiel.my_finances.data.projection.pool.PoolProjector
import com.al3xiel.my_finances.data.projection.pool.ReimbursementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.StatementFormat
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.loans.data.LoanRepositoryImpl
import com.al3xiel.my_finances.feature.loans.domain.rules.LoanVoidGuard
import com.al3xiel.my_finances.feature.loans.domain.usecase.GrantLoanUseCase
import com.al3xiel.my_finances.feature.loans.domain.usecase.ReceiveLoanRepaymentUseCase
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
import com.al3xiel.my_finances.feature.pools.data.PoolRepositoryImpl
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolExpenseGuard
import com.al3xiel.my_finances.feature.pools.domain.usecase.CreatePoolUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.GenerateSettlementStatementUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.ReceiveReimbursementUseCase
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * La traza de `docs/04 §6` con su cúmulo (#5, #6 y #11: gasto de S/ 120 en "Perros – mamá" y reembolso de S/ 120) de
 * punta a punta: casos de uso reales, registro de eventos real y Room real. Después del replay las proyecciones tienen
 * que ser idénticas, fila por fila; también con reembolso parcial, rendición, anulación y una deuda suelta.
 */
@RunWith(AndroidJUnit4::class)
class PoolsExampleTraceReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var pools: PoolRepositoryImpl
    private lateinit var loans: LoanRepositoryImpl
    private lateinit var replay: ReplayProjectionsUseCase

    private val today = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"))
    private val ids = UuidIdGenerator()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private lateinit var createAccount: CreateAccountUseCase
    private lateinit var createPerson: CreatePersonUseCase
    private lateinit var createIncomeSource: CreateIncomeSourceUseCase
    private lateinit var createCategory: CreateCategoryUseCase
    private lateinit var createPool: CreatePoolUseCase
    private lateinit var registerExpense: RegisterExpenseUseCase
    private lateinit var registerIncome: RegisterIncomeUseCase
    private lateinit var registerTransfer: RegisterTransferUseCase
    private lateinit var receiveReimbursement: ReceiveReimbursementUseCase
    private lateinit var generateStatement: GenerateSettlementStatementUseCase
    private lateinit var grantLoan: GrantLoanUseCase
    private lateinit var receiveRepayment: ReceiveLoanRepaymentUseCase
    private lateinit var voidMovement: VoidMovementUseCase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        val refresher = PoolPeriodRefresher(database.poolDao())
        val poster = LedgerPoster(database.movementDao(), database.ledgerDao())
        // Desordenados a propósito: el orden lo pone `EventProjector.order`, igual en vivo y en el replay.
        val projectors = setOf(
            LoanProjector(database.loanDao(), poster),
            ReimbursementProjector(database.reimbursementDao(), poster, refresher),
            PoolProjector(database.poolDao(), refresher),
            MovementProjector(database.movementDao(), database.ledgerDao()),
            CatalogProjector(database.catalogDao()),
        )
        store = EventStoreImpl(database, projectors, ids, clock, "1.0")
        accounts = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())
        val categories = CategoryRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val tags = TagRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val people = PersonRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val sources = IncomeSourceRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val movements = MovementRepositoryImpl(database.movementDao(), database.movementQueryDao(), store)
        pools = PoolRepositoryImpl(database.poolQueryDao())
        loans = LoanRepositoryImpl(database.loanQueryDao())

        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val poolGuard = PoolExpenseGuard(pools)
        createAccount = CreateAccountUseCase(accounts, recorder, ids)
        createPerson = CreatePersonUseCase(people, recorder, ids)
        createIncomeSource = CreateIncomeSourceUseCase(sources, people, recorder, ids)
        createCategory = CreateCategoryUseCase(categories, recorder, ids)
        createPool = CreatePoolUseCase(pools, people, categories, tags, recorder, ids)
        registerExpense = RegisterExpenseUseCase(rules, accounts, recorder, ids, setOf(poolGuard))
        registerIncome = RegisterIncomeUseCase(rules, recorder, ids)
        registerTransfer = RegisterTransferUseCase(rules, accounts, recorder, ids)
        val lock = MovementWriteLock()
        receiveReimbursement = ReceiveReimbursementUseCase(pools, people, rules, recorder, ids, lock)
        generateStatement = GenerateSettlementStatementUseCase(pools, people, recorder, ids, lock)
        grantLoan = GrantLoanUseCase(people, accounts, rules, recorder, ids, lock)
        receiveRepayment = ReceiveLoanRepaymentUseCase(loans, rules, recorder, ids, lock)
        voidMovement = VoidMovementUseCase(movements, accounts, rules, recorder, lock, setOf(poolGuard, LoanVoidGuard(loans)))
        replay = ReplayProjectionsUseCase(store, ProjectionRebuilderImpl(database, projectors, store, clock), ids, clock)
    }

    @After
    fun tearDown() = database.close()

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    private suspend fun balances() = accounts.observeAccounts().first().associate { it.account.name to it.balanceMinor }

    private fun scalar(sql: String): String? =
        database.openHelper.readableDatabase.query(sql).use { if (it.moveToFirst()) it.getString(0) else null }

    private fun dump(table: String, order: String): List<List<String?>> =
        database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY $order").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
            }
        }

    private fun projections() = mapOf(
        "accounts" to dump("accounts", "id"),
        "categories" to dump("categories", "id"),
        "people" to dump("people", "id"),
        "income_sources" to dump("income_sources", "id"),
        "movements" to dump("movements", "id"),
        "movement_parts" to dump("movement_parts", "id"),
        "movement_part_tags" to dump("movement_part_tags", "part_id, tag_id"),
        "ledger_entries" to dump("ledger_entries", "id"),
        "account_balances" to dump("account_balances", "account_id"),
        "pools" to dump("pools", "id"),
        "pool_periods" to dump("pool_periods", "id"),
        "settlement_statements" to dump("settlement_statements", "id"),
        "reimbursements" to dump("reimbursements", "id"),
        "reimbursement_allocations" to dump("reimbursement_allocations", "reimbursement_id, part_id"),
        "loans" to dump("loans", "id"),
        "loan_repayments" to dump("loan_repayments", "id"),
    )

    private suspend fun assertReplayLeavesTheSameProjections() {
        val eventsBefore = store.readAll().size
        assertEquals(ChainVerification.Valid(eventsBefore), VerifyEventChainUseCase(store)())
        val before = projections()
        val balancesBefore = balances()

        replay("test de la traza con cúmulos")

        val after = projections()
        before.forEach { (table, rows) -> assertEquals("tabla $table", rows, after[table]) }
        assertEquals(before, after)
        assertEquals(balancesBefore, balances())
        assertEquals(eventsBefore + 1, store.readAll().size)
        assertEquals(ChainVerification.Valid(eventsBefore + 1), VerifyEventChainUseCase(store)())
        listOf("catalog", "movement", "pool", "reimbursement", "loan").forEach {
            assertNotNull("proyector $it", database.projectionStateDao().state(it)?.rebuiltAt)
        }
    }

    private class World(
        val bbva: AccountId,
        val bcp: AccountId,
        val cash: AccountId,
        val mama: PersonId,
        val source: IncomeSourceId,
        val pets: CategoryId,
        val transport: CategoryId,
        val pool: PoolId,
    )

    /** #1–#5: cuentas, Mamá, categorías y el cúmulo "Perros – mamá". */
    private suspend fun world(): World {
        createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val bcp = createAccount("BCP", AccountType.BANK, null, pen(0)).value()
        val cash = createAccount("Efectivo", AccountType.CASH, null, pen(0)).value()
        val bbva = accounts.observeAccounts().first().first { it.account.name == "BBVA" }.account.id
        val mama = createPerson("Mamá").value()
        val source = createIncomeSource("Mamá", mama).value()
        val pets = createCategory("Comida mascotas", "pets", "#8D6E63", null).value()
        val transport = createCategory("Transporte", "bus", "#64B5F6", null).value()
        val pool = createPool("Perros – mamá", mama, PoolKind.PERMANENT, pets, emptyList()).value()
        return World(bbva, bcp, cash, mama, source, pets, transport, pool)
    }

    private fun inPool(w: World, minor: Long) = listOf(ExpensePartInput(null, pen(minor), w.pets, emptyList(), w.pool))

    @Test
    fun theDocumentedTraceWithAPoolYieldsTheDocumentedBalancesAndReplaysIdentically() = runBlocking {
        val w = world()

        // #6 gasto de BBVA S/ 120 en el cúmulo · #7 ingreso de BCP S/ 50 · #9 retiro S/ 100 · #10 gasto de Efectivo S/ 5.
        registerExpense(w.bbva, pen(12_000), inPool(w, 12_000), "Comida de los perros", today).value()
        registerIncome(w.bcp, pen(5_000), w.source, "Para pasajes", today).value()
        registerTransfer(w.bbva, w.cash, pen(10_000), null, "Retiro", today).value()
        registerExpense(w.cash, pen(500), listOf(ExpensePartInput(null, pen(500), w.transport, emptyList())), "Pasaje", today).value()
        assertEquals(12_000L, pools.pendingMinor(w.pool))

        // #11 Mamá devuelve S/ 120 a BCP: salda la parte del #6.
        receiveReimbursement(w.mama, w.bcp, pen(12_000), AllocationStrategy.OLDEST_FIRST, w.pool, emptyList(), null, today).value()

        // Docs/04 §6 sin fondos: BBVA 780 · BCP 170 · Efectivo 95 · Por cobrar 0.
        assertEquals(mapOf("BBVA" to 78_000L, "BCP" to 17_000L, "Efectivo" to 9_500L), balances())
        assertEquals(0L, pools.pendingMinor(w.pool))
        assertEquals("REIMBURSED", scalar("SELECT reimbursement_status FROM movement_parts WHERE pool_id IS NOT NULL"))
        assertEquals("12000", scalar("SELECT reimbursed_minor FROM movement_parts WHERE pool_id IS NOT NULL"))
        assertEquals("12000", scalar("SELECT reimbursed_minor FROM pool_periods"))

        assertReplayLeavesTheSameProjections()
    }

    @Test
    fun aPartialReimbursementAStatementAVoidAndALoanAllSurviveTheReplay() = runBlocking {
        val w = world()
        registerExpense(w.bbva, pen(10_000), inPool(w, 10_000), "Veterinario", today).value()

        // Reembolso parcial de S/ 40: la parte queda parcialmente reembolsada.
        val partial = receiveReimbursement(w.mama, w.bbva, pen(4_000), AllocationStrategy.OLDEST_FIRST, w.pool, emptyList(), null, today).value()
        assertEquals("PARTIALLY_REIMBURSED", scalar("SELECT reimbursement_status FROM movement_parts WHERE pool_id IS NOT NULL"))
        assertEquals(6_000L, pools.pendingMinor(w.pool))

        // Rendición: cierra el periodo con S/ 60 pendientes y abre otro; el gasto siguiente cae en el nuevo.
        val statement = generateStatement(w.pool, StatementFormat.PDF).value()
        assertEquals(6_000L, statement.content.pendingTotal.amountMinor)
        registerExpense(w.bbva, pen(3_000), inPool(w, 3_000), "Juguete", today).value()
        assertEquals(2, scalar("SELECT COUNT(*) FROM pool_periods")?.toInt())
        assertEquals("STATEMENT_SENT", scalar("SELECT status FROM pool_periods WHERE closed_on IS NOT NULL"))
        assertEquals("3000", scalar("SELECT total_minor FROM pool_periods WHERE closed_on IS NULL"))
        assertEquals(9_000L, pools.pendingMinor(w.pool))

        // Anular el reembolso lo revierte todo: la parte vuelve a pendiente y la cuenta pierde los S/ 40.
        assertEquals(Outcome.Ok(Unit), voidMovement(partial, "monto mal", confirmNegativeBalance = false))
        assertEquals("PENDING", scalar("SELECT reimbursement_status FROM movement_parts WHERE amount_minor = 10000"))
        assertEquals(13_000L, pools.pendingMinor(w.pool))
        assertEquals(100_000L - 10_000 - 3_000, balances().getValue("BBVA"))

        // Una deuda suelta: S/ 50 a Pepe, dos pagos y se anula el primero.
        val pepe = createPerson("Pepe").value()
        val loan = grantLoan(pepe, w.bbva, pen(5_000), "Taxi", today).value()
        val first: MovementId = receiveRepayment(loan, w.bbva, pen(2_000), today).value()
        receiveRepayment(loan, w.bbva, pen(1_000), today).value()
        assertEquals(Outcome.Ok(Unit), voidMovement(first, "duplicado", confirmNegativeBalance = false))
        assertEquals("1000", scalar("SELECT repaid_minor FROM loans"))
        assertEquals("OPEN", scalar("SELECT status FROM loans"))
        assertEquals(100_000L - 10_000 - 3_000 - 5_000 + 1_000, balances().getValue("BBVA"))

        assertReplayLeavesTheSameProjections()
    }
}
