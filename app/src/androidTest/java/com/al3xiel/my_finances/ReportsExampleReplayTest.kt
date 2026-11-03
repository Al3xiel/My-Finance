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
import com.al3xiel.my_finances.data.projection.fund.FundProjector
import com.al3xiel.my_finances.data.projection.movement.LedgerPoster
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodRefresher
import com.al3xiel.my_finances.data.projection.pool.PoolProjector
import com.al3xiel.my_finances.data.projection.pool.ReimbursementProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationProjector
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.FeeData
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.funds.data.FundRepositoryImpl
import com.al3xiel.my_finances.feature.funds.domain.rules.FundExpenseGuard
import com.al3xiel.my_finances.feature.funds.domain.rules.FundMovementGuards
import com.al3xiel.my_finances.feature.funds.domain.usecase.CountFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.CreateFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.RegisterIncomeWithTopUpUseCase
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
import com.al3xiel.my_finances.feature.pools.data.PoolRepositoryImpl
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolExpenseGuard
import com.al3xiel.my_finances.feature.pools.domain.usecase.CreatePoolUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.ReceiveReimbursementUseCase
import com.al3xiel.my_finances.feature.reconciliation.data.ReconciliationRepositoryImpl
import com.al3xiel.my_finances.feature.reconciliation.domain.rules.ReconciliationGuards
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.RecordBalanceCheckUseCase
import com.al3xiel.my_finances.feature.reconciliation.domain.usecase.ResolveReconciliationUseCase
import com.al3xiel.my_finances.feature.reports.data.ReportRepositoryImpl
import com.al3xiel.my_finances.feature.reports.domain.model.ReportPeriod
import com.al3xiel.my_finances.feature.reports.domain.rules.ReportCalculator
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El escenario de `docs/04 §5.6` de punta a punta con casos de uso, registro de eventos y Room reales: un gasto de S/ 120 en
 * un cúmulo en octubre y su reembolso en noviembre (octubre sigue con S/ 120 de gasto propio, noviembre con 0), un reembolso
 * anulado, una comisión, un gasto no registrado, un ajuste, una corrección y una anulación. Los reportes tienen que dar los
 * mismos números después de un replay.
 */
@RunWith(AndroidJUnit4::class)
class ReportsExampleReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var reports: ReportRepositoryImpl
    private lateinit var replay: ReplayProjectionsUseCase

    private val today = LocalDate.of(2026, 11, 20)
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"))
    private val ids = UuidIdGenerator()
    private val october = ReportPeriod.Month(YearMonth.of(2026, 10))
    private val november = ReportPeriod.Month(YearMonth.of(2026, 11))

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)
    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    private lateinit var createAccount: CreateAccountUseCase
    private lateinit var createPerson: CreatePersonUseCase
    private lateinit var createIncomeSource: CreateIncomeSourceUseCase
    private lateinit var createCategory: CreateCategoryUseCase
    private lateinit var createPool: CreatePoolUseCase
    private lateinit var createFund: CreateFundUseCase
    private lateinit var registerExpense: RegisterExpenseUseCase
    private lateinit var registerIncomeWithTopUp: RegisterIncomeWithTopUpUseCase
    private lateinit var registerTransfer: RegisterTransferUseCase
    private lateinit var receiveReimbursement: ReceiveReimbursementUseCase
    private lateinit var countFund: CountFundUseCase
    private lateinit var correct: CorrectMovementUseCase
    private lateinit var voidMovement: VoidMovementUseCase
    private lateinit var record: RecordBalanceCheckUseCase
    private lateinit var resolve: ResolveReconciliationUseCase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        val refresher = PoolPeriodRefresher(database.poolDao())
        val poster = LedgerPoster(database.movementDao(), database.ledgerDao())
        // Desordenados a propósito: el orden lo pone `EventProjector.order`, igual en vivo y en el replay.
        val projectors = setOf(
            ReconciliationProjector(database.reconciliationDao(), poster),
            FundProjector(database.fundDao()),
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
        val pools = PoolRepositoryImpl(database.poolQueryDao())
        val funds = FundRepositoryImpl(database.fundQueryDao())
        val reconciliations = ReconciliationRepositoryImpl(database.reconciliationQueryDao())
        reports = ReportRepositoryImpl(database.reportQueryDao(), clock)

        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val lock = MovementWriteLock()
        val poolGuard = PoolExpenseGuard(pools)
        val linkGuards = setOf(ReconciliationGuards(reconciliations))
        createAccount = CreateAccountUseCase(accounts, recorder, ids)
        createPerson = CreatePersonUseCase(people, recorder, ids)
        createIncomeSource = CreateIncomeSourceUseCase(sources, people, recorder, ids)
        createCategory = CreateCategoryUseCase(categories, recorder, ids)
        createPool = CreatePoolUseCase(pools, people, categories, tags, recorder, ids)
        createFund = CreateFundUseCase(funds, recorder, ids)
        registerExpense = RegisterExpenseUseCase(rules, accounts, recorder, ids, lock, setOf(poolGuard), setOf(FundExpenseGuard(funds)))
        registerIncomeWithTopUp = RegisterIncomeWithTopUpUseCase(rules, funds, recorder, ids, lock)
        registerTransfer = RegisterTransferUseCase(rules, accounts, recorder, ids)
        receiveReimbursement = ReceiveReimbursementUseCase(pools, people, rules, recorder, ids, lock)
        countFund = CountFundUseCase(funds, rules, recorder, ids, lock)
        correct = CorrectMovementUseCase(rules, accounts, movements, recorder, ids, lock, emptySet(), emptySet(), emptySet(), linkGuards)
        voidMovement = VoidMovementUseCase(movements, accounts, rules, recorder, lock, setOf(poolGuard, FundMovementGuards(funds, movements)))
        record = RecordBalanceCheckUseCase(accounts, reconciliations, recorder, ids)
        resolve = ResolveReconciliationUseCase(reconciliations, recorder, ids, lock)
        replay = ReplayProjectionsUseCase(store, ProjectionRebuilderImpl(database, projectors, store, clock), ids, clock)
    }

    @After
    fun tearDown() = database.close()

    /** Todo lo que los reportes muestran de un periodo, en una sola cosa comparable. */
    private suspend fun snapshot(period: ReportPeriod): List<Any> {
        val parts = reports.parts(period)
        val ledger = reports.ledger(period)
        return listOf(
            ReportCalculator.summary(parts, ledger), ReportCalculator.byCategory(parts), ReportCalculator.byTag(parts),
            ReportCalculator.byAccount(ledger), reports.partDetails(period),
        )
    }

    @Test
    fun theOctoberAndNovemberReportsMatchTheScenarioAndSurviveAReplay() = runBlocking {
        createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val bbva = accounts.observeAccounts().first().first { it.account.name == "BBVA" }.account.id
        val bcp = createAccount("BCP", AccountType.BANK, null, pen(0)).value()
        val cash = createAccount("Efectivo", AccountType.CASH, null, pen(0)).value()
        val mama = createPerson("Mamá").value()
        val salary = createIncomeSource("Sueldo", null).value()
        val pets = createCategory("Comida mascotas", "pets", "#8D6E63", null).value()
        val transport = createCategory("Transporte", "bus", "#64B5F6", null).value()
        val fees = createCategory("Comisiones", "fee", "#9E9E9E", null).value()
        val pool = createPool("Perros – mamá", mama, PoolKind.PERMANENT, pets, emptyList()).value()
        val fare = createFund("Pasajes", ConsumptionMode.DETAILED, null, oct(1), null).value()

        // Octubre: S/ 120 y S/ 50 en el cúmulo, un gasto propio corregido, otro anulado, ingreso con recarga y retiro con comisión.
        registerExpense(bbva, pen(12_000), listOf(ExpensePartInput(null, pen(12_000), pets, emptyList(), pool)), "Veterinario", oct(10)).value()
        registerExpense(bbva, pen(5_000), listOf(ExpensePartInput(null, pen(5_000), pets, emptyList(), pool)), "Juguetes", oct(20)).value()
        val bus = registerExpense(bbva, pen(3_000), listOf(ExpensePartInput(null, pen(3_000), transport, emptyList())), "Pasaje", oct(12)).value()
        correct(bus, MovementDraft.Expense(bbva, pen(4_000), listOf(ExpensePartInput(null, pen(4_000), transport, emptyList())), "Pasaje", oct(12)), "monto").value()
        val dup = registerExpense(bbva, pen(2_000), listOf(ExpensePartInput(null, pen(2_000), transport, emptyList())), "Repetido", oct(12)).value()
        voidMovement(dup, "duplicado", confirmNegativeBalance = false).value()
        registerIncomeWithTopUp(bcp, pen(50_000), salary, "Sueldo", oct(15), fare, pen(5_000)).value()
        registerTransfer(bbva, cash, pen(10_000), FeeData(pen(50), fees), "Retiro", oct(16)).value()

        // Un reembolso de octubre que luego se anula: no cuenta. El real de noviembre sí, pero no cambia octubre.
        val voidedBack = receiveReimbursement(mama, bcp, pen(5_000), AllocationStrategy.OLDEST_FIRST, pool, emptyList(), null, oct(25)).value()
        voidMovement(voidedBack, "monto mal", confirmNegativeBalance = false).value()
        receiveReimbursement(mama, bcp, pen(12_000), AllocationStrategy.OLDEST_FIRST, pool, emptyList(), null, nov(5)).value()

        // Noviembre: arqueo con S/ 12 sin anotar y un ajuste de conciliación.
        assertEquals(pen(1_200), countFund(fare, pen(3_800), cash, transport).value())
        val check = record(bcp, pen(61_000)).value()
        resolve(check, ReconciliationCause.UNKNOWN, "no encontré el origen").value()

        val octoberBefore = snapshot(october)
        val novemberBefore = snapshot(november)

        // Octubre mide al 31: S/ 120 + S/ 50 del cúmulo siguen siendo propios, más S/ 40 corregidos y S/ 0,50 de comisión.
        val octSummary = ReportCalculator.summary(reports.parts(october), reports.ledger(october))
        assertEquals(50_000L, octSummary.income)
        assertEquals(12_000L + 5_000 + 4_000 + 50, octSummary.ownSpend)
        assertEquals(17_000L, octSummary.others.total)
        assertEquals(0L, octSummary.others.reimbursed)
        assertEquals(0L, octSummary.unidentified)
        assertEquals(10_000, ReportCalculator.byCategory(reports.parts(october)).sumOf { it.basisPoints })

        // Noviembre: el reembolso de octubre no entra en las partes de noviembre; solo el gasto no registrado.
        val novSummary = ReportCalculator.summary(reports.parts(november), reports.ledger(november))
        assertEquals(1_200L, novSummary.ownSpend)
        assertEquals(0L, novSummary.others.total)
        assertTrue("el ajuste va en No identificado", novSummary.unidentified != 0L)

        replay("test del escenario de reportes")

        assertEquals(octoberBefore, snapshot(october))
        assertEquals(novemberBefore, snapshot(november))
    }
}
