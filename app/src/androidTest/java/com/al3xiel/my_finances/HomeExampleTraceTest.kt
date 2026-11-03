package com.al3xiel.my_finances

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.money.MoneyFormatter
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.ProjectorSetFactory
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
import com.al3xiel.my_finances.domain.model.event.ConsumptionMode
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.expectedincome.data.ExpectedIncomeRepositoryImpl
import com.al3xiel.my_finances.feature.funds.data.FundRepositoryImpl
import com.al3xiel.my_finances.feature.funds.domain.rules.FundExpenseGuard
import com.al3xiel.my_finances.feature.funds.domain.usecase.CreateFundUseCase
import com.al3xiel.my_finances.feature.funds.domain.usecase.RegisterIncomeWithTopUpUseCase
import com.al3xiel.my_finances.feature.home.domain.model.HomeSummary
import com.al3xiel.my_finances.feature.home.domain.usecase.ObserveHomeSummaryUseCase
import com.al3xiel.my_finances.feature.home.ui.HomeContent
import com.al3xiel.my_finances.feature.home.ui.HomeUiState
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
import com.al3xiel.my_finances.feature.pools.data.PoolRepositoryImpl
import com.al3xiel.my_finances.feature.pools.data.ReceivablesRepositoryImpl
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolExpenseGuard
import com.al3xiel.my_finances.feature.pools.domain.usecase.CreatePoolUseCase
import com.al3xiel.my_finances.feature.pools.domain.usecase.ReceiveReimbursementUseCase
import com.al3xiel.my_finances.feature.reconciliation.data.ReconciliationRepositoryImpl
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * La traza de `docs/04 §6` completa (cuentas, cúmulo, fondo Pasajes, reembolso) leída por Inicio con repositorios reales:
 * los cuatro indicadores salen de las proyecciones, "ocultar montos" los enmascara y un movimiento nuevo actualiza Inicio
 * sin recargar. `FLAG_SECURE` es de la ventana (lo cubre `MainActivitySecurityTest`); Inicio no toca la ventana.
 */
@RunWith(AndroidJUnit4::class)
class HomeExampleTraceTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var database: AppDatabase
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var home: ObserveHomeSummaryUseCase
    private lateinit var registerExpense: RegisterExpenseUseCase
    private lateinit var world: World

    private val today = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"))
    private val ids = UuidIdGenerator()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private class World(val cash: AccountId, val transport: CategoryId)

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    private val backupSettings = object : BackupSettingsRepository {
        override val enabled: Flow<Boolean> = MutableStateFlow(false)
        override val intervalDays: Flow<Int> = MutableStateFlow(BackupSettingsRepository.DEFAULT_INTERVAL_DAYS)
        override suspend fun isEnabled() = false
        override suspend fun setEnabled(enabled: Boolean) = Unit
        override suspend fun setIntervalDays(days: Int) = Unit
        override suspend fun lastRemindedAt(): Instant? = null
        override suspend fun markReminded(at: Instant) = Unit
    }

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        val store = EventStoreImpl(database, ProjectorSetFactory(database).create(), ids, clock, "1.0")
        accounts = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())
        val categories = CategoryRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val tags = TagRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val people = PersonRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val sources = IncomeSourceRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val pools = PoolRepositoryImpl(database.poolQueryDao())
        val funds = FundRepositoryImpl(database.fundQueryDao())

        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val lock = MovementWriteLock()
        registerExpense = RegisterExpenseUseCase(
            rules, accounts, recorder, ids, lock, setOf(PoolExpenseGuard(pools)), setOf(FundExpenseGuard(funds)),
        )
        val createAccount = CreateAccountUseCase(accounts, recorder, ids)
        val createPerson = CreatePersonUseCase(people, recorder, ids)
        val createIncomeSource = CreateIncomeSourceUseCase(sources, people, recorder, ids)
        val createCategory = CreateCategoryUseCase(categories, recorder, ids)
        val createPool = CreatePoolUseCase(pools, people, categories, tags, recorder, ids)
        val createFund = CreateFundUseCase(funds, recorder, ids)
        val registerIncomeWithTopUp = RegisterIncomeWithTopUpUseCase(rules, funds, recorder, ids, lock)
        val registerTransfer = RegisterTransferUseCase(rules, accounts, recorder, ids)
        val receiveReimbursement = ReceiveReimbursementUseCase(pools, people, rules, recorder, ids, lock)

        // #1–#5: cuentas, Mamá, categorías y el cúmulo; el fondo Pasajes.
        createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val bcp = createAccount("BCP", AccountType.BANK, null, pen(0)).value()
        val cash = createAccount("Efectivo", AccountType.CASH, null, pen(0)).value()
        val bbva = accounts.observeAccounts().first().first { it.account.name == "BBVA" }.account.id
        val mama = createPerson("Mamá").value()
        val source = createIncomeSource("Mamá", mama).value()
        val pets = createCategory("Comida mascotas", "pets", "#8D6E63", null).value()
        val transport = createCategory("Transporte", "bus", "#64B5F6", null).value()
        val pool = createPool("Perros – mamá", mama, PoolKind.PERMANENT, pets, emptyList()).value()
        val fare = createFund("Pasajes", ConsumptionMode.DETAILED, null, today, null).value()
        world = World(cash, transport)

        // #6 gasto en el cúmulo · #7 y #8 ingreso con recarga · #9 retiro · #10 gasto con fondo · #11 reembolso.
        registerExpense(bbva, pen(12_000), listOf(ExpensePartInput(null, pen(12_000), pets, emptyList(), pool)), "Comida de los perros", today).value()
        registerIncomeWithTopUp(bcp, pen(5_000), source, "Para pasajes", today, fare, pen(5_000)).value()
        registerTransfer(bbva, cash, pen(10_000), null, "Retiro", today).value()
        registerExpense(cash, pen(500), listOf(ExpensePartInput(null, pen(500), transport, emptyList(), null, fare)), "Pasaje", today).value()
        receiveReimbursement(mama, bcp, pen(12_000), AllocationStrategy.OLDEST_FIRST, pool, emptyList(), null, today).value()

        home = ObserveHomeSummaryUseCase(
            accounts,
            funds,
            ReceivablesRepositoryImpl(database.poolQueryDao()),
            ExpectedIncomeRepositoryImpl(database.expectedIncomeQueryDao(), clock),
            ReconciliationRepositoryImpl(database.reconciliationQueryDao()),
            ObserveBackupStatusUseCase(store, backupSettings, clock),
            clock,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun theDocumentedTraceYieldsTheDocumentedIndicators() = runBlocking {
        val summary = home().first()

        // Docs/04 §6: BBVA 780 · BCP 170 · Efectivo 95 · Apartado Pasajes 45 · Por cobrar 0 · Disponible libre 1,000.
        assertEquals(
            mapOf("BBVA" to 78_000L, "BCP" to 17_000L, "Efectivo" to 9_500L),
            summary.accounts.associate { it.account.name to it.balanceMinor },
        )
        assertEquals(pen(104_500), summary.accountsBalance)
        assertEquals(pen(4_500), summary.reserved)
        assertEquals(pen(100_000), summary.freeAvailable)
        assertEquals(pen(0), summary.receivable)
        assertEquals(listOf("Pasajes"), summary.funds.map { it.fund.name })
        assertEquals(emptyList<Any>(), summary.alerts)
    }

    @Test
    fun hidingAmountsMasksTheFourNumbersAndTheRows() {
        val summary = runBlocking { home().first() }
        rule.setContent {
            CompositionLocalProvider(LocalHideAmounts provides true) {
                HomeContent(HomeUiState(summary, hideAmounts = true), {}, {}, {}, {}, {})
            }
        }

        listOf(104_500L, 4_500L, 100_000L, 78_000L, 17_000L, 9_500L).forEach {
            rule.onNodeWithText(MoneyFormatter.format(pen(it))).assertDoesNotExist()
        }
        rule.onNodeWithText("BBVA").assertExists()
    }

    @Test
    fun changingAMovementUpdatesHomeWithoutReloading() = runBlocking {
        val latest = MutableStateFlow<HomeSummary?>(null)
        val job = launch(Dispatchers.IO) { home().collect { latest.value = it } }
        try {
            assertEquals(pen(104_500), withTimeout(5_000) { latest.filterNotNull().first() }.accountsBalance)

            val part = ExpensePartInput(null, pen(300), world.transport, emptyList())
            registerExpense(world.cash, pen(300), listOf(part), "Otro pasaje", today).value()

            val updated = withTimeout(5_000) { latest.filterNotNull().first { it.accountsBalance == pen(104_200) } }
            assertEquals(9_200L, updated.accounts.first { it.account.name == "Efectivo" }.balanceMinor)
        } finally {
            job.cancel()
        }
    }
}
