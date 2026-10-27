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
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.RegisterTransferUseCase
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
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
 * La traza de `docs/04 §6` sin cúmulos ni fondos, de punta a punta: casos de uso reales, registro de eventos
 * real y Room real. Después del replay las proyecciones tienen que ser idénticas, fila por fila.
 */
@RunWith(AndroidJUnit4::class)
class ExampleTraceReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var replay: ReplayProjectionsUseCase

    private val today = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"))
    private val ids = UuidIdGenerator()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private lateinit var createAccount: CreateAccountUseCase
    private lateinit var createPerson: CreatePersonUseCase
    private lateinit var createIncomeSource: CreateIncomeSourceUseCase
    private lateinit var createCategory: CreateCategoryUseCase
    private lateinit var registerExpense: RegisterExpenseUseCase
    private lateinit var registerIncome: RegisterIncomeUseCase
    private lateinit var registerTransfer: RegisterTransferUseCase
    private lateinit var correctMovement: CorrectMovementUseCase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        val projectors = setOf(
            CatalogProjector(database.catalogDao()),
            MovementProjector(database.movementDao(), database.ledgerDao()),
        )
        store = EventStoreImpl(database, projectors, ids, clock, "1.0")
        accounts = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())
        val categories = CategoryRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val tags = TagRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val people = PersonRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val sources = IncomeSourceRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val movements = MovementRepositoryImpl(database.movementDao(), database.movementQueryDao(), store)

        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        createAccount = CreateAccountUseCase(accounts, recorder, ids)
        createPerson = CreatePersonUseCase(people, recorder, ids)
        createIncomeSource = CreateIncomeSourceUseCase(sources, people, recorder, ids)
        createCategory = CreateCategoryUseCase(categories, recorder, ids)
        registerExpense = RegisterExpenseUseCase(rules, accounts, recorder, ids)
        registerIncome = RegisterIncomeUseCase(rules, recorder, ids)
        registerTransfer = RegisterTransferUseCase(rules, accounts, recorder, ids)
        correctMovement = CorrectMovementUseCase(rules, accounts, movements, recorder, ids, MovementWriteLock())
        replay = ReplayProjectionsUseCase(store, ProjectionRebuilderImpl(database, projectors, store, clock), ids, clock)
    }

    @After
    fun tearDown() = database.close()

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    private suspend fun balances() = accounts.observeAccounts().first().associate { it.account.name to it.balanceMinor }

    private fun dump(table: String, order: String): List<List<String?>> =
        database.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY $order").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
            }
        }

    private fun projections() = mapOf(
        "accounts" to dump("accounts", "id"),
        "categories" to dump("categories", "id"),
        "tags" to dump("tags", "id"),
        "people" to dump("people", "id"),
        "income_sources" to dump("income_sources", "id"),
        "movements" to dump("movements", "id"),
        "movement_parts" to dump("movement_parts", "id"),
        "movement_part_tags" to dump("movement_part_tags", "part_id, tag_id"),
        "ledger_entries" to dump("ledger_entries", "id"),
        "account_balances" to dump("account_balances", "account_id"),
    )

    @Test
    fun theExampleTraceYieldsTheDocumentedBalancesAndReplaysIdentically() = runBlocking {
        // #1–#3 Cuentas: BBVA S/ 1,000, BCP y Efectivo en cero.
        createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val bcp = createAccount("BCP", AccountType.BANK, null, pen(0)).value()
        val cash = createAccount("Efectivo", AccountType.CASH, null, pen(0)).value()
        val bbva = accounts.observeAccounts().first().first { it.account.name == "BBVA" }.account.id

        // #4 Persona y su fuente de ingreso; categorías de los gastos.
        val mama = createPerson("Mamá").value()
        val mamaSource: IncomeSourceId = createIncomeSource("Mamá", mama).value()
        val pets: CategoryId = createCategory("Comida mascotas", "pets", "#8D6E63", null).value()
        val transport: CategoryId = createCategory("Transporte", "bus", "#64B5F6", null).value()

        // #6 Gasto de BBVA S/ 120 · #7 ingreso de BCP S/ 50 · #9 retiro BBVA → Efectivo S/ 100 · #10 gasto de Efectivo S/ 5.
        registerExpense(bbva, pen(12_000), listOf(ExpensePartInput(null, pen(12_000), pets, emptyList())), "Comida de los perros", today).value()
        registerIncome(bcp, pen(5_000), mamaSource, "Para pasajes", today).value()
        registerTransfer(bbva, cash, pen(10_000), null, "Retiro", today).value()
        val busFare: MovementId = registerExpense(cash, pen(500), listOf(ExpensePartInput(null, pen(500), transport, emptyList())), "Pasaje", today).value()

        // Sin cúmulos ni fondos ni el reembolso del #11: 1,000 − 120 − 100 / 50 / 100 − 5.
        assertEquals(mapOf("BBVA" to 78_000L, "BCP" to 5_000L, "Efectivo" to 9_500L), balances())

        // Una corrección con motivo también tiene que sobrevivir al replay.
        val corrected = correctMovement(
            busFare,
            MovementDraft.Expense(cash, pen(450), listOf(ExpensePartInput(null, pen(450), transport, emptyList())), "Pasaje", today),
            "me cobraron S/ 4.50",
        )
        assertEquals(Outcome.Ok(Unit), corrected)
        assertEquals(9_550L, balances().getValue("Efectivo"))

        val eventsBefore = store.readAll().size
        assertEquals(ChainVerification.Valid(eventsBefore), VerifyEventChainUseCase(store)())
        val before = projections()
        val balancesBefore = balances()

        replay("test de la traza de ejemplo")

        assertEquals(before, projections())
        assertEquals(balancesBefore, balances())
        // El replay deja constancia (un evento más) y la cadena de hashes sigue íntegra.
        assertEquals(eventsBefore + 1, store.readAll().size)
        assertEquals(ChainVerification.Valid(eventsBefore + 1), VerifyEventChainUseCase(store)())
        assertNotNull(database.projectionStateDao().state("movement")?.rebuiltAt)
        assertNotNull(database.projectionStateDao().state("catalog")?.rebuiltAt)
    }

    @Test
    fun rebuildingFromScratchOnAnEmptyProjectionRestoresEverything() = runBlocking {
        createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val before = projections()

        // Se pierden las proyecciones (no los eventos): el replay las reconstruye.
        database.openHelper.writableDatabase.apply {
            listOf("ledger_entries", "account_balances", "accounts").forEach { execSQL("DELETE FROM $it") }
        }
        replay("proyecciones perdidas")

        assertEquals(before, projections())
        assertEquals(listOf("BBVA"), accounts.observeAccounts().first().map { it.account.name })
    }
}
