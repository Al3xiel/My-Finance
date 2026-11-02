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
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.FundId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.RemainderPolicy
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
import com.al3xiel.my_finances.feature.funds.data.FundRepositoryImpl
import com.al3xiel.my_finances.feature.funds.domain.rules.FundExpenseGuard
import com.al3xiel.my_finances.feature.funds.domain.rules.FundMovementGuards
import com.al3xiel.my_finances.feature.funds.domain.usecase.CloseFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.CountFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.CreateFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.MarkUsageDayUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.RegisterIncomeWithTopUpUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.TopUpFundUseCase
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
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
 * La traza de `docs/04 §6` con su fondo (#7 y #8: ingreso de S/ 50 con recarga vinculada; #10: gasto de S/ 5 con fondo
 * Pasajes → Apartado 45) de punta a punta: casos de uso reales, registro de eventos real y Room real. Después del replay
 * las proyecciones tienen que ser idénticas, fila por fila; también con días de uso, un arqueo con gasto no registrado,
 * un gasto anulado y un cierre con `ROLL_OVER`.
 */
@RunWith(AndroidJUnit4::class)
class FundsExampleTraceReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var funds: FundRepositoryImpl
    private lateinit var replay: ReplayProjectionsUseCase

    private val today = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"))
    private val ids = UuidIdGenerator()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private lateinit var createAccount: CreateAccountUseCase
    private lateinit var createPerson: CreatePersonUseCase
    private lateinit var createIncomeSource: CreateIncomeSourceUseCase
    private lateinit var createCategory: CreateCategoryUseCase
    private lateinit var createFund: CreateFundUseCase
    private lateinit var topUpFund: TopUpFundUseCase
    private lateinit var markUsageDay: MarkUsageDayUseCase
    private lateinit var countFund: CountFundUseCase
    private lateinit var closeFund: CloseFundUseCase
    private lateinit var registerIncomeWithTopUp: RegisterIncomeWithTopUpUseCase
    private lateinit var registerExpense: RegisterExpenseUseCase
    private lateinit var registerTransfer: RegisterTransferUseCase
    private lateinit var voidMovement: VoidMovementUseCase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        // Desordenados a propósito: el orden lo pone `EventProjector.order`, igual en vivo y en el replay.
        val projectors = setOf(
            FundProjector(database.fundDao()),
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
        funds = FundRepositoryImpl(database.fundQueryDao())

        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val lock = MovementWriteLock()
        createAccount = CreateAccountUseCase(accounts, recorder, ids)
        createPerson = CreatePersonUseCase(people, recorder, ids)
        createIncomeSource = CreateIncomeSourceUseCase(sources, people, recorder, ids)
        createCategory = CreateCategoryUseCase(categories, recorder, ids)
        createFund = CreateFundUseCase(funds, recorder, ids)
        topUpFund = TopUpFundUseCase(funds, recorder, lock)
        markUsageDay = MarkUsageDayUseCase(funds, recorder, lock)
        countFund = CountFundUseCase(funds, rules, recorder, ids, lock)
        closeFund = CloseFundUseCase(funds, recorder, lock)
        registerIncomeWithTopUp = RegisterIncomeWithTopUpUseCase(rules, funds, recorder, ids, lock)
        registerExpense = RegisterExpenseUseCase(rules, accounts, recorder, ids, lock, emptySet(), setOf(FundExpenseGuard(funds)))
        registerTransfer = RegisterTransferUseCase(rules, accounts, recorder, ids)
        voidMovement = VoidMovementUseCase(movements, accounts, rules, recorder, lock, setOf(FundMovementGuards(funds, movements)))
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
        "funds" to dump("funds", "id"),
        "fund_usage_days" to dump("fund_usage_days", "fund_id, day"),
        "fund_counts" to dump("fund_counts", "id"),
        "fund_topups" to dump("fund_topups", "id"),
    )

    private suspend fun assertReplayLeavesTheSameProjections() {
        val eventsBefore = store.readAll().size
        assertEquals(ChainVerification.Valid(eventsBefore), VerifyEventChainUseCase(store)())
        val before = projections()
        val balancesBefore = balances()
        val summaryBefore = funds.observeSummary().first()

        replay("test de la traza con fondos")

        val after = projections()
        before.forEach { (table, rows) -> assertEquals("tabla $table", rows, after[table]) }
        assertEquals(before, after)
        assertEquals(balancesBefore, balances())
        assertEquals(summaryBefore, funds.observeSummary().first())
        assertEquals(eventsBefore + 1, store.readAll().size)
        assertEquals(ChainVerification.Valid(eventsBefore + 1), VerifyEventChainUseCase(store)())
        listOf("catalog", "movement", "fund").forEach {
            assertNotNull("proyector $it", database.projectionStateDao().state(it)?.rebuiltAt)
        }
    }

    private class World(
        val bbva: AccountId,
        val bcp: AccountId,
        val cash: AccountId,
        val source: IncomeSourceId,
        val transport: CategoryId,
    )

    /** #1–#4: cuentas, Mamá como fuente de ingreso y la categoría Transporte. */
    private suspend fun world(): World {
        createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val bcp = createAccount("BCP", AccountType.BANK, null, pen(0)).value()
        val cash = createAccount("Efectivo", AccountType.CASH, null, pen(0)).value()
        val bbva = accounts.observeAccounts().first().first { it.account.name == "BBVA" }.account.id
        val mama = createPerson("Mamá").value()
        val source = createIncomeSource("Mamá", mama).value()
        val transport = createCategory("Transporte", "bus", "#64B5F6", null).value()
        return World(bbva, bcp, cash, source, transport)
    }

    private fun withFund(w: World, minor: Long, fund: FundId) = listOf(ExpensePartInput(null, pen(minor), w.transport, emptyList(), null, fund))

    @Test
    fun theDocumentedTraceWithAFundYieldsTheDocumentedBalancesAndReplaysIdentically() = runBlocking {
        val w = world()
        val fare = createFund("Pasajes", ConsumptionMode.DETAILED, null, today, null).value()

        // #7 ingreso de BCP S/ 50 y #8 recarga vinculada (una sola correlación) · #9 retiro S/ 100 · #10 gasto de Efectivo S/ 5.
        val income = registerIncomeWithTopUp(w.bcp, pen(5_000), w.source, "Para pasajes", today, fare, pen(5_000)).value()
        registerTransfer(w.bbva, w.cash, pen(10_000), null, "Retiro", today).value()
        registerExpense(w.cash, pen(500), withFund(w, 500, fare), "Pasaje", today).value()

        // Docs/04 §6 sin cúmulo: BBVA 900 · BCP 50 · Efectivo 95 · Apartado Pasajes 45 · Disponible libre 1,000.
        assertEquals(mapOf("BBVA" to 90_000L, "BCP" to 5_000L, "Efectivo" to 9_500L), balances())
        val pasajes = funds.find(fare)
        assertEquals(5_000L, pasajes?.fund?.allocated?.amountMinor)
        assertEquals(500L, pasajes?.detailedSpent?.amountMinor)
        assertEquals(4_500L, pasajes?.reserved?.amountMinor)
        val summary = funds.observeSummary().first()
        assertEquals(4_500L, summary.reserved.amountMinor)
        assertEquals(100_000L, summary.available.amountMinor)
        assertEquals(5_000L, funds.toppedUpFrom(income).amountMinor)
        assertEquals(1, scalar("SELECT COUNT(DISTINCT correlation_id) FROM events WHERE type IN ('IncomeRegistered', 'FundToppedUp')")?.toInt())

        assertReplayLeavesTheSameProjections()
    }

    @Test
    fun usageDaysACountWithUntrackedSpendAVoidAndARollOverCloseAllSurviveTheReplay() = runBlocking {
        val w = world()
        val fare = createFund("Pasajes", ConsumptionMode.DAILY_ESTIMATE, pen(500), LocalDate.of(2026, 10, 20), null).value()
        val food = createFund("Comida", ConsumptionMode.DETAILED, null, today, null).value()

        registerIncomeWithTopUp(w.bcp, pen(5_000), w.source, "Para pasajes", today, fare, pen(5_000)).value()
        registerTransfer(w.bcp, w.cash, pen(5_000), null, "Retiro", today).value()
        markUsageDay(fare, LocalDate.of(2026, 10, 24), marked = true).value()
        markUsageDay(fare, LocalDate.of(2026, 10, 25), marked = true).value()
        markUsageDay(fare, LocalDate.of(2026, 10, 25), marked = false).value()
        registerExpense(w.cash, pen(500), withFund(w, 500, fare), "Pasaje", today).value()
        val duplicated = registerExpense(w.cash, pen(300), withFund(w, 300, fare), "Pasaje repetido", today).value()
        voidMovement(duplicated, "duplicado", confirmNegativeBalance = false).value()

        // Arqueo: asignado 50 − detallado 5 (el anulado no cuenta) − declarado 38 = S/ 7 que no se anotaron.
        assertEquals(pen(700), countFund(fare, pen(3_800), w.cash, w.transport).value())
        assertEquals("1", scalar("SELECT COUNT(*) FROM movements WHERE type = 'FUND_UNTRACKED_SPEND'"))
        assertEquals(mapOf("BBVA" to 100_000L, "BCP" to 0L, "Efectivo" to 3_800L), balances())
        assertEquals(1, scalar("SELECT COUNT(*) FROM fund_usage_days")?.toInt())
        assertEquals("1200", scalar("SELECT real_spent_minor FROM fund_counts"))
        assertEquals("700", scalar("SELECT deviation_minor FROM fund_counts"))

        // Recarga con dinero propio en Comida y cierre de Pasajes pasando el sobrante (50 − 5 − 7 = 38) a Comida.
        topUpFund(food, pen(1_000)).value()
        closeFund(fare, RemainderPolicy.ROLL_OVER, food).value()
        assertEquals(4_800L, funds.find(food)?.fund?.allocated?.amountMinor)
        assertEquals(0L, funds.find(fare)?.reserved?.amountMinor)
        assertEquals("CLOSED", scalar("SELECT status FROM funds WHERE name = 'Pasajes'"))

        assertReplayLeavesTheSameProjections()
    }
}
