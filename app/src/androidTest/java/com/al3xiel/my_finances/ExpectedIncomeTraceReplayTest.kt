package com.al3xiel.my_finances

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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
import com.al3xiel.my_finances.data.projection.expectedincome.ExpectedIncomeProjector
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.ReplayProjectionsUseCase
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.expectedincome.data.ExpectedIncomeRepositoryImpl
import com.al3xiel.my_finances.feature.expectedincome.data.ReminderPreferences
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderNotifier
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedIncomeVoidHooks
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ExpectedRange
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.CancelExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ConfirmExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.RemindOverdueExpectedIncomesUseCase
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.ScheduleExpectedIncomeUseCase
import com.al3xiel.my_finances.feature.funds.data.FundRepositoryImpl
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.VoidMovementUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Reloj que la prueba adelanta a mano: así el esperado "vence" sin esperar. */
private class MutableClock(var day: LocalDate, private val zone: ZoneId) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(day, zone)
    override fun instant(): Instant = day.atTime(15, 0).atZone(zone).toInstant()
}

/**
 * El flujo F7 de `docs/05` de punta a punta, con casos de uso reales, registro de eventos real y Room real:
 * programar "Sueldo, BBVA, ~S/ 2,000, entre el 28 y el 5, mensual" → vence → recordatorio → confirmar (ingreso,
 * confirmado y siguiente en una correlación) → anular el ingreso (vuelve a pendiente y el siguiente se cancela).
 * Después del replay las proyecciones tienen que ser idénticas, fila por fila.
 */
@RunWith(AndroidJUnit4::class)
class ExpectedIncomeTraceReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var expectedIncomes: ExpectedIncomeRepositoryImpl
    private lateinit var replay: ReplayProjectionsUseCase
    private lateinit var scope: CoroutineScope

    private val zone = ZoneId.of("America/Lima")
    private val clock = MutableClock(LocalDate.of(2026, 10, 26), zone)
    private val ids = UuidIdGenerator()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private lateinit var createAccount: CreateAccountUseCase
    private lateinit var createIncomeSource: CreateIncomeSourceUseCase
    private lateinit var schedule: ScheduleExpectedIncomeUseCase
    private lateinit var confirm: ConfirmExpectedIncomeUseCase
    private lateinit var cancel: CancelExpectedIncomeUseCase
    private lateinit var voidMovement: VoidMovementUseCase
    private lateinit var remind: RemindOverdueExpectedIncomesUseCase
    private lateinit var reminders: ReminderPreferences
    private var notices = 0

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        // Desordenados a propósito: el orden lo pone `EventProjector.order`, igual en vivo y en el replay.
        val projectors = setOf(
            ExpectedIncomeProjector(database.expectedIncomeDao()),
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
        val funds = FundRepositoryImpl(database.fundQueryDao())
        expectedIncomes = ExpectedIncomeRepositoryImpl(database.expectedIncomeQueryDao(), clock)

        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val lock = MovementWriteLock()
        createAccount = CreateAccountUseCase(accounts, recorder, ids)
        createIncomeSource = CreateIncomeSourceUseCase(sources, people, recorder, ids)
        schedule = ScheduleExpectedIncomeUseCase(rules, recorder, ids)
        confirm = ConfirmExpectedIncomeUseCase(rules, expectedIncomes, funds, recorder, ids, lock)
        cancel = CancelExpectedIncomeUseCase(expectedIncomes, recorder, lock)
        voidMovement = VoidMovementUseCase(
            movements, accounts, rules, recorder, lock, emptySet(), setOf(ExpectedIncomeVoidHooks(expectedIncomes)),
        )
        replay = ReplayProjectionsUseCase(store, ProjectionRebuilderImpl(database, projectors, store, clock), ids, clock)

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        reminders = ReminderPreferences(
            PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "f7-${ids.newId()}.preferences_pb") },
        )
        remind = RemindOverdueExpectedIncomesUseCase(expectedIncomes, reminders, ReminderNotifier { notices++; true }, clock)
    }

    @After
    fun tearDown() {
        database.close()
        scope.cancel()
    }

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    private suspend fun balances() = accounts.observeAccounts().first().associate { it.account.name to it.balanceMinor }

    private suspend fun status(id: ExpectedIncomeId) = expectedIncomes.find(id)?.status

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
        "income_sources" to dump("income_sources", "id"),
        "movements" to dump("movements", "id"),
        "ledger_entries" to dump("ledger_entries", "id"),
        "account_balances" to dump("account_balances", "account_id"),
        "expected_incomes" to dump("expected_incomes", "id"),
    )

    private suspend fun assertReplayLeavesTheSameProjections() {
        val eventsBefore = store.readAll().size
        assertEquals(ChainVerification.Valid(eventsBefore), VerifyEventChainUseCase(store)())
        val before = projections()
        val balancesBefore = balances()
        val listBefore = expectedIncomes.observeAll().first()

        replay("test de la traza de ingresos esperados")

        val after = projections()
        before.forEach { (table, rows) -> assertEquals("tabla $table", rows, after[table]) }
        assertEquals(before, after)
        assertEquals(balancesBefore, balances())
        assertEquals(listBefore, expectedIncomes.observeAll().first())
        assertEquals(eventsBefore + 1, store.readAll().size)
        assertEquals(ChainVerification.Valid(eventsBefore + 1), VerifyEventChainUseCase(store)())
        listOf("catalog", "movement", "expected_income").forEach {
            assertNotNull("proyector $it", database.projectionStateDao().state(it)?.rebuiltAt)
        }
    }

    private suspend fun world(): Pair<AccountId, IncomeSourceId> {
        createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val bbva = accounts.observeAccounts().first().first { it.account.name == "BBVA" }.account.id
        return bbva to createIncomeSource("Sueldo", null).value()
    }

    @Test
    fun f7ScheduleOverdueConfirmVoidAndRemindEndsAsDocumentedAndReplaysIdentically() = runBlocking {
        val (bbva, sueldo) = world()
        remindersOn()

        // Hoy 26 oct: "Sueldo, BBVA, ~S/ 2,000, entre el 28 y el 5, mensual".
        val first = schedule(bbva, sueldo, pen(200_000), ExpectedRange(day(10, 28), day(11, 5)), repeatsMonthly = true).value()
        assertEquals(ExpectedIncomeStatus.SCHEDULED, status(first))
        assertEquals(first, expectedIncomes.next()?.id)
        assertEquals(0, remind())

        // Pasa el 5 sin confirmar: vence y el primer recordatorio sale al día siguiente; una sola vez por corrida.
        clock.day = day(11, 6)
        assertEquals(ExpectedIncomeStatus.OVERDUE, status(first))
        assertEquals(listOf(first), expectedIncomes.overdue().map { it.id })
        assertEquals(1, remind())
        assertEquals(0, remind())
        assertEquals(1, notices)

        // Confirmar abre S23 prellenado: ingreso real de S/ 2,050 → ingreso + confirmado + siguiente, una correlación.
        val income = confirm(first, bbva, pen(205_000), sueldo, "Sueldo octubre", day(11, 6)).value()
        val second = expectedIncomes.find(first)?.nextExpectedId
        assertNotNull(second)
        assertEquals(ExpectedIncomeStatus.CONFIRMED, status(first))
        assertEquals(ExpectedIncomeStatus.SCHEDULED, status(second!!))
        assertEquals(ExpectedRange(day(11, 28), day(12, 5)), expectedIncomes.find(second)?.range)
        assertEquals(mapOf("BBVA" to 305_000L), balances())
        assertEquals(1, scalar("SELECT COUNT(DISTINCT correlation_id) FROM events WHERE type IN ('IncomeRegistered', 'ExpectedIncomeConfirmed')")?.toInt())
        assertEquals(
            scalar("SELECT correlation_id FROM events WHERE type = 'IncomeRegistered'"),
            scalar("SELECT correlation_id FROM events WHERE type = 'ExpectedIncomeScheduled' ORDER BY sequence DESC LIMIT 1"),
        )
        // Ya confirmado, no hay nada vencido que avisar.
        clock.day = day(11, 9)
        assertEquals(0, remind())

        // Anular el ingreso: el primero vuelve a pendiente (vencido) y el siguiente se cancela con motivo, en una correlación.
        clock.day = day(11, 10)
        voidMovement(income, "monto equivocado", confirmNegativeBalance = false).value()
        assertEquals(ExpectedIncomeStatus.OVERDUE, status(first))
        assertEquals(ExpectedIncomeStatus.CANCELLED, status(second))
        assertEquals(mapOf("BBVA" to 100_000L), balances())
        assertEquals(1, scalar("SELECT COUNT(DISTINCT correlation_id) FROM events WHERE type IN ('MovementVoided') OR (type = 'ExpectedIncomeCancelled')")?.toInt())

        // El Worker retoma el ritmo de cada 3 días y se detiene en el tercer aviso (el de antes cuenta).
        assertEquals(1, remind()) // 10 nov: pasaron 4 días desde el aviso del 6 → segundo.
        clock.day = day(11, 12)
        assertEquals(0, remind())
        clock.day = day(11, 13)
        assertEquals(1, remind()) // tercero.
        clock.day = day(11, 30)
        assertEquals(0, remind())
        assertEquals(3, notices)

        assertReplayLeavesTheSameProjections()
    }

    @Test
    fun cancellingTheOccurrenceEndsTheSeriesAndSurvivesTheReplay() = runBlocking {
        val (bbva, sueldo) = world()
        val first = schedule(bbva, sueldo, pen(200_000), ExpectedRange(day(10, 28), day(11, 5)), repeatsMonthly = true).value()

        cancel(first, "ya no cobro ahí").value()

        assertEquals(ExpectedIncomeStatus.CANCELLED, status(first))
        assertEquals("ya no cobro ahí", expectedIncomes.find(first)?.cancelReason)
        assertEquals(1, expectedIncomes.observeAll().first().size)
        assertReplayLeavesTheSameProjections()
    }

    private suspend fun remindersOn() = reminders.setEnabled(true)

    private suspend fun remind() = remind.invoke()
}
