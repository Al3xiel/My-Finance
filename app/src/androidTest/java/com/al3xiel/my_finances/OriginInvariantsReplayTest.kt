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
import com.al3xiel.my_finances.data.projection.origin.OriginProjector
import com.al3xiel.my_finances.data.projection.pool.PoolPeriodRefresher
import com.al3xiel.my_finances.data.projection.pool.PoolProjector
import com.al3xiel.my_finances.data.projection.pool.ReimbursementProjector
import com.al3xiel.my_finances.data.projection.reconciliation.ReconciliationProjector
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.AllocationStrategy
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
import com.al3xiel.my_finances.feature.loans.data.LoanRepositoryImpl
import com.al3xiel.my_finances.feature.loans.domain.usecase.GrantLoanUseCase
import com.al3xiel.my_finances.feature.loans.domain.usecase.ReceiveLoanRepaymentUseCase
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.CorrectMovementUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
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
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import java.time.Clock
import java.time.LocalDate
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
 * Invariantes del origen del dinero (docs/04 §5.1) con casos de uso, registro de eventos y Room reales: la suma del origen
 * de cada cuenta es su saldo y cada salida queda atribuida por completo, en cada paso del escenario; lo calculado en vivo es
 * igual a lo que deja un replay; y el replay de 5 000 asientos entra en un presupuesto de tiempo.
 */
@RunWith(AndroidJUnit4::class)
class OriginInvariantsReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var accounts: AccountRepositoryImpl
    private lateinit var replay: ReplayProjectionsUseCase

    private val today = LocalDate.of(2026, 11, 20)
    private val zone = ZoneId.of("America/Lima")
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(zone).toInstant(), zone)
    private val ids = UuidIdGenerator()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)
    private fun oct(day: Int) = LocalDate.of(2026, 10, day)
    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    private lateinit var createAccount: CreateAccountUseCase
    private lateinit var createPerson: CreatePersonUseCase
    private lateinit var createIncomeSource: CreateIncomeSourceUseCase
    private lateinit var createCategory: CreateCategoryUseCase
    private lateinit var createPool: CreatePoolUseCase
    private lateinit var registerExpense: RegisterExpenseUseCase
    private lateinit var registerIncome: RegisterIncomeUseCase
    private lateinit var registerTransfer: RegisterTransferUseCase
    private lateinit var receiveReimbursement: ReceiveReimbursementUseCase
    private lateinit var grantLoan: GrantLoanUseCase
    private lateinit var receiveRepayment: ReceiveLoanRepaymentUseCase
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
        // Desordenados a propósito: el orden lo pone `EventProjector.order`, y el origen corre al final.
        val projectors = setOf(
            OriginProjector(database.originDao()),
            ReconciliationProjector(database.reconciliationDao(), poster),
            LoanProjector(database.loanDao(), poster),
            ReimbursementProjector(database.reimbursementDao(), poster, refresher),
            PoolProjector(database.poolDao(), refresher),
            MovementProjector(database.movementDao(), database.ledgerDao()),
            CatalogProjector(database.catalogDao()),
        )
        val store = EventStoreImpl(database, projectors, ids, clock, "1.0")
        accounts = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())
        val categories = CategoryRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val tags = TagRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val people = PersonRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val sources = IncomeSourceRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val movements = MovementRepositoryImpl(database.movementDao(), database.movementQueryDao(), store)
        val pools = PoolRepositoryImpl(database.poolQueryDao())
        val loans = LoanRepositoryImpl(database.loanQueryDao())
        val reconciliations = ReconciliationRepositoryImpl(database.reconciliationQueryDao())

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
        registerExpense = RegisterExpenseUseCase(rules, accounts, recorder, ids, lock, setOf(poolGuard), emptySet())
        registerIncome = RegisterIncomeUseCase(rules, recorder, ids, linkGuards)
        registerTransfer = RegisterTransferUseCase(rules, accounts, recorder, ids)
        receiveReimbursement = ReceiveReimbursementUseCase(pools, people, rules, recorder, ids, lock)
        grantLoan = GrantLoanUseCase(people, accounts, rules, recorder, ids, lock)
        receiveRepayment = ReceiveLoanRepaymentUseCase(loans, rules, recorder, ids, lock)
        correct = CorrectMovementUseCase(rules, accounts, movements, recorder, ids, lock, emptySet(), emptySet(), emptySet(), linkGuards)
        voidMovement = VoidMovementUseCase(movements, accounts, rules, recorder, lock, setOf(poolGuard))
        record = RecordBalanceCheckUseCase(accounts, reconciliations, recorder, ids)
        resolve = ResolveReconciliationUseCase(reconciliations, recorder, ids, lock)
        replay = ReplayProjectionsUseCase(store, ProjectionRebuilderImpl(database, projectors, store, clock), ids, clock)
    }

    @After
    fun tearDown() = database.close()

    private fun rows(sql: String): List<List<String>> = database.openHelper.readableDatabase.query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
    }

    private suspend fun balanceOf(id: AccountId) = accounts.balanceMinor(id)

    /** Suma del origen por cuenta = saldo, y toda salida con atribuciones suma lo que salió. */
    private suspend fun assertInvariants(step: String) {
        accounts.observeAccounts().first().forEach { item ->
            val origin = rows("SELECT COALESCE(SUM(amount_minor), 0) FROM origin_balances WHERE account_id = '${item.account.id.value}'")
                .single().single().toLong()
            assertEquals("$step: origen de ${item.account.name}", item.balanceMinor, origin)
        }
        val broken = rows(
            "SELECT l.id FROM ledger_entries l WHERE l.is_voided = 0 AND l.delta_minor < 0 " +
                "AND l.entry_kind IN ('EXPENSE','FEE','LOAN_OUT','ADJUSTMENT') " +
                "AND (SELECT COALESCE(SUM(a.amount_minor), 0) FROM origin_attributions a WHERE a.ledger_entry_id = l.id) <> -l.delta_minor",
        )
        assertTrue("$step: salidas sin atribuir por completo: $broken", broken.isEmpty())
        val orphans = rows(
            "SELECT a.ledger_entry_id FROM origin_attributions a LEFT JOIN ledger_entries l ON l.id = a.ledger_entry_id " +
                "WHERE l.id IS NULL OR l.is_voided = 1",
        )
        assertTrue("$step: atribuciones de asientos anulados o inexistentes: $orphans", orphans.isEmpty())
    }

    private fun originState() = listOf(
        rows("SELECT account_id, income_source_id, amount_minor FROM origin_balances ORDER BY 1, 2"),
        rows("SELECT ledger_entry_id, part_id, income_source_id, amount_minor FROM origin_attributions ORDER BY 1, 2, 3"),
        rows("SELECT id, last_order_key, last_sequence, last_entry_id FROM origin_cursor"),
    )

    @Test
    fun theOriginAddsUpAtEveryStepAndAReplayLeavesTheSameState() = runBlocking {
        val bbva = createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
        val bcp = createAccount("BCP", AccountType.BANK, null, pen(0)).value()
        val cash = createAccount("Efectivo", AccountType.CASH, null, pen(0)).value()
        val mama = createPerson("Mamá").value()
        val salary = createIncomeSource("Sueldo", null).value()
        val freelance = createIncomeSource("Freelance", null).value()
        val food = createCategory("Comida", "food", "#8D6E63", null).value()
        val pets = createCategory("Mascotas", "pets", "#64B5F6", null).value()
        val pool = createPool("Perros – mamá", mama, PoolKind.PERMANENT, pets, emptyList()).value()
        assertInvariants("apertura")

        registerIncome(bcp, pen(50_000), salary, "Sueldo", oct(2)).value()
        registerIncome(bbva, pen(20_000), freelance, "Proyecto", oct(3)).value()
        val old = registerExpense(bbva, pen(30_000), listOf(ExpensePartInput(null, pen(30_000), food, emptyList())), "Súper", oct(4)).value()
        val dup = registerExpense(bbva, pen(10_000), listOf(ExpensePartInput(null, pen(10_000), food, emptyList())), "Repetido", oct(5)).value()
        assertInvariants("ingresos y gastos")

        // Transferencias de varios saltos: BCP → BBVA → Efectivo → BCP, la última con comisión.
        registerTransfer(bcp, bbva, pen(30_000), null, "Pase", oct(6)).value()
        registerTransfer(bbva, cash, pen(40_000), null, "Retiro", oct(7)).value()
        registerTransfer(cash, bcp, pen(5_000), FeeData(pen(50), food), "Depósito", oct(8)).value()
        assertInvariants("transferencias")

        // Un gasto que excede el saldo de la cuenta: el faltante va a "ajuste no identificado".
        registerExpense(cash, pen(60_000), listOf(ExpensePartInput(null, pen(60_000), food, emptyList())), "Fiesta", oct(9), confirmNegativeBalance = true).value()
        assertInvariants("gasto que excede el saldo")
        assertTrue("el efectivo quedó en negativo", balanceOf(cash) < 0)

        // Cúmulo y reembolso, préstamo y cobro.
        registerExpense(bbva, pen(12_000), listOf(ExpensePartInput(null, pen(12_000), pets, emptyList(), pool)), "Veterinario", oct(10)).value()
        receiveReimbursement(mama, bcp, pen(12_000), AllocationStrategy.OLDEST_FIRST, pool, emptyList(), null, LocalDate.of(2026, 11, 5)).value()
        val loan = grantLoan(mama, bcp, pen(8_000), "Préstamo", oct(11)).value()
        receiveRepayment(loan, bbva, pen(8_000), oct(25)).value()
        assertInvariants("reembolso, préstamo y cobro")

        // Conciliación: un ajuste positivo y otro negativo.
        val up = record(bcp, pen(balanceOf(bcp) + 1_200)).value()
        resolve(up, ReconciliationCause.UNKNOWN, "sobra").value()
        val down = record(bbva, pen(balanceOf(bbva) - 500)).value()
        resolve(down, ReconciliationCause.UNKNOWN, "falta").value()
        assertInvariants("ajustes de conciliación")

        // Corregir y anular movimientos antiguos: todo lo posterior se recalcula.
        correct(old, MovementDraft.Expense(bbva, pen(45_000), listOf(ExpensePartInput(null, pen(45_000), food, emptyList())), "Súper", oct(4)), "monto").value()
        assertInvariants("corrección de un movimiento antiguo")
        voidMovement(dup, "duplicado", confirmNegativeBalance = true).value()
        assertInvariants("anulación de un movimiento antiguo")

        val live = originState()
        assertTrue("hay origen calculado", live[0].isNotEmpty() && live[1].isNotEmpty())
        replay("test de invariantes de origen")
        assertInvariants("después del replay")
        assertEquals("lo calculado en vivo es igual al replay", live, originState())
    }

    @Test
    fun replayingFiveThousandEntriesFitsTheTimeBudget() = runBlocking {
        val db = database.openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("INSERT INTO categories VALUES ('food','Food','i','c',NULL,0)")
            (1..5_000).forEach { i ->
                val account = "a${i % 4}"
                val income = i % 5 < 2
                val movement = "m$i"
                val source = if (income) "'s${i % 50}'" else "NULL"
                val type = if (income) "INCOME" else "EXPENSE"
                val amount = 1_000L + (i * 37L) % 900
                val day = "2026-10-${(i % 28 + 1).toString().padStart(2, '0')}"
                db.execSQL(
                    "INSERT INTO movements (id, type, account_id, counter_account_id, amount_minor, currency, income_source_id, person_id, note, " +
                        "occurred_on, status, revision, last_sequence) VALUES ('$movement','$type','$account',NULL,$amount,'PEN',$source,NULL,NULL,'$day','ACTIVE',1,$i)",
                )
                if (!income) {
                    db.execSQL(
                        "INSERT INTO movement_parts (id, movement_id, amount_minor, category_id, kind, pool_id, reimbursed_minor, reimbursement_status, created_sequence) " +
                            "VALUES ('p$i','$movement',$amount,'food','NORMAL',NULL,0,'PENDING',$i)",
                    )
                }
                val delta = if (income) amount else -amount
                val kind = if (income) "INCOME" else "EXPENSE"
                db.execSQL("INSERT INTO ledger_entries VALUES ('l$i',$i,'$movement','$account',$delta,'$kind','$day',0,NULL)")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val projector = OriginProjector(database.originDao())
        val started = System.nanoTime()
        projector.reset()
        projector.beginReplay()
        projector.finishReplay()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        val first = originState()

        assertTrue("el replay de 5 000 asientos tardó $elapsedMs ms (presupuesto: 10 000 ms)", elapsedMs < 10_000)
        (0..3).forEach { a ->
            val total = rows("SELECT COALESCE(SUM(delta_minor), 0) FROM ledger_entries WHERE account_id = 'a$a'").single().single().toLong()
            val origin = rows("SELECT COALESCE(SUM(amount_minor), 0) FROM origin_balances WHERE account_id = 'a$a'").single().single().toLong()
            assertEquals("origen de a$a", total, origin)
        }
        projector.reset()
        projector.beginReplay()
        projector.finishReplay()
        assertEquals("el replay es determinista", first, originState())
    }
}
