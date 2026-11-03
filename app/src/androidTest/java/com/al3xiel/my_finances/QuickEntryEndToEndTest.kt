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
import com.al3xiel.my_finances.data.projection.ProjectorSetFactory
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.data.AccountRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.data.IncomeSourceRepositoryImpl
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateAccountUseCase
import com.al3xiel.my_finances.feature.accounts.domain.usecase.CreateIncomeSourceUseCase
import com.al3xiel.my_finances.feature.categories.data.CategoryRepositoryImpl
import com.al3xiel.my_finances.feature.categories.domain.usecase.CreateCategoryUseCase
import com.al3xiel.my_finances.feature.funds.data.FundRepositoryImpl
import com.al3xiel.my_finances.feature.funds.domain.rules.FundExpenseGuard
import com.al3xiel.my_finances.feature.movements.data.MovementRepositoryImpl
import com.al3xiel.my_finances.feature.movements.domain.model.ExpensePartInput
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterExpenseUseCase
import com.al3xiel.my_finances.feature.movements.domain.usecase.RegisterIncomeUseCase
import com.al3xiel.my_finances.feature.people.data.PersonRepositoryImpl
import com.al3xiel.my_finances.feature.people.domain.usecase.CreatePersonUseCase
import com.al3xiel.my_finances.feature.pools.data.PoolRepositoryImpl
import com.al3xiel.my_finances.feature.pools.domain.rules.PoolExpenseGuard
import com.al3xiel.my_finances.feature.pools.domain.usecase.CreatePoolUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.ConfirmQuickEntryUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.LoadCatalogSnapshotUseCase
import com.al3xiel.my_finances.feature.quickentry.domain.usecase.SuggestDefaultsUseCase
import com.al3xiel.my_finances.feature.quickentry.ui.QuickEntryUiAction
import com.al3xiel.my_finances.feature.quickentry.ui.QuickEntryUiState
import com.al3xiel.my_finances.feature.quickentry.ui.QuickEntryViewModel
import com.al3xiel.my_finances.feature.tags.data.TagRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/**
 * Registro rápido con repositorios reales (base Room en memoria): cada línea debe dejar los mismos movimientos y saldos que
 * los mismos datos por los casos de uso de los editores, en un mundo gemelo. La pantalla no se toca; se maneja su ViewModel.
 */
@RunWith(AndroidJUnit4::class)
class QuickEntryEndToEndTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val clock = Clock.fixed(today.atTime(15, 0).atZone(ZoneId.of("America/Lima")).toInstant(), ZoneId.of("America/Lima"))
    private val worlds = mutableListOf<World>()

    private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

    private fun <T> Outcome<T>.value(): T = (this as Outcome.Ok).value

    /** Cuentas BBVA (1,000) y BCP, categorías Mercado / Mercería / Comida mascotas, fuente Sueldo y el cúmulo Perros. */
    private inner class World {
        val database: AppDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        val ids = UuidIdGenerator()
        val store = EventStoreImpl(database, ProjectorSetFactory(database).create(), ids, clock, "1.0")
        val accounts = AccountRepositoryImpl(database.catalogDao(), database.catalogQueryDao(), database.ledgerQueryDao())
        val categories = CategoryRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val tags = TagRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val people = PersonRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val sources = IncomeSourceRepositoryImpl(database.catalogDao(), database.catalogQueryDao())
        val pools = PoolRepositoryImpl(database.poolQueryDao())
        val funds = FundRepositoryImpl(database.fundQueryDao())
        val movements = MovementRepositoryImpl(database.movementDao(), database.movementQueryDao(), store)
        val recorder = EventRecorder(store, ids, clock)
        val rules = MovementRules(accounts, categories, tags, sources, clock)
        val registerExpense = RegisterExpenseUseCase(
            rules, accounts, recorder, ids, MovementWriteLock(), setOf(PoolExpenseGuard(pools)), setOf(FundExpenseGuard(funds)),
        )
        val registerIncome = RegisterIncomeUseCase(rules, recorder, ids)

        var bbva = AccountId("")
        var bcp = AccountId("")
        var market = CategoryId("")
        var pets = CategoryId("")
        var salary = IncomeSourceId("")
        var dogs = PoolId("")

        init {
            runBlocking {
                val createAccount = CreateAccountUseCase(accounts, recorder, ids)
                val createCategory = CreateCategoryUseCase(categories, recorder, ids)
                bbva = createAccount("BBVA", AccountType.BANK, null, pen(100_000)).value()
                bcp = createAccount("BCP", AccountType.BANK, null, pen(0)).value()
                market = createCategory("Mercado", "cart", "#8D6E63", null).value()
                createCategory("Mercería", "cart", "#64B5F6", null).value()
                pets = createCategory("Comida mascotas", "pets", "#8D6E63", null).value()
                val mom = CreatePersonUseCase(people, recorder, ids)("Mamá").value()
                salary = CreateIncomeSourceUseCase(sources, people, recorder, ids)("Sueldo", null).value()
                dogs = CreatePoolUseCase(pools, people, categories, tags, recorder, ids)("Perros", mom, PoolKind.PERMANENT, pets, emptyList()).value()
            }
            worlds += this
        }

        fun quickEntry() = QuickEntryViewModel(
            LoadCatalogSnapshotUseCase(accounts, categories, tags, pools, people, funds, sources),
            SuggestDefaultsUseCase(movements, accounts, categories, pools),
            ConfirmQuickEntryUseCase(registerExpense, registerIncome, pools, clock),
        )

        suspend fun balances() = accounts.observeAccounts().first().associate { it.account.name to it.balanceMinor }

        /** Lo que el movimiento dice, sin los ids que genera cada mundo. */
        suspend fun lastExpense() = movements.lastExpense()?.let { m ->
            listOf(m.accountId, m.amount, m.note, m.occurredOn) + m.parts.flatMap { listOf(it.amount, it.categoryId, it.tagIds, it.poolId, it.fundId) }
        }
    }

    @After
    fun tearDown() = worlds.forEach { it.database.close() }

    private suspend fun QuickEntryViewModel.ready(): QuickEntryViewModel = also {
        withTimeout(10_000) { uiState.first { it.accountOptions.isNotEmpty() } }
    }

    private suspend fun QuickEntryViewModel.type(line: String): QuickEntryUiState {
        onAction(QuickEntryUiAction.EditLine(line))
        return withTimeout(10_000) { uiState.first { it.line == line } }
    }

    private suspend fun QuickEntryViewModel.save() {
        onAction(QuickEntryUiAction.Save)
        withTimeout(10_000) { uiState.first { it.saved } }
    }

    @Test
    fun anExpenseLineLeavesTheSameMovementAndBalancesAsTheEditor() = runBlocking {
        val text = World()
        val editor = World()

        val vm = text.quickEntry().ready()
        assertTrue(vm.type("45 bbva mercado").canSave)
        vm.save()
        editor.registerExpense(editor.bbva, pen(4_500), listOf(ExpensePartInput(null, pen(4_500), editor.market, emptyList())), null, today).value()

        assertEquals(mapOf("BBVA" to 95_500L, "BCP" to 0L), text.balances())
        assertEquals(editor.balances(), text.balances())
        assertNotNull(text.lastExpense())
        assertEquals(editor.lastExpense()?.map { it.toString() }?.let(::normalize), text.lastExpense()?.map { it.toString() }?.let(::normalize))
    }

    @Test
    fun anIncomeLineLeavesTheSameBalancesAsTheEditor() = runBlocking {
        val text = World()
        val editor = World()

        val vm = text.quickEntry().ready()
        assertTrue(vm.type("+50 bcp sueldo").canSave)
        vm.save()
        editor.registerIncome(editor.bcp, pen(5_000), editor.salary, null, today).value()

        assertEquals(mapOf("BBVA" to 100_000L, "BCP" to 5_000L), text.balances())
        assertEquals(editor.balances(), text.balances())
    }

    @Test
    fun anAmbiguousLineMustBeChosenBeforeSaving() = runBlocking {
        val text = World()
        val vm = text.quickEntry().ready()

        val state = vm.type("45 bbva mer")
        assertEquals(1, state.ambiguities.size)
        assertFalse(state.canSave)
        vm.onAction(QuickEntryUiAction.Save)
        assertEquals(mapOf("BBVA" to 100_000L, "BCP" to 0L), text.balances())

        val ambiguity = state.ambiguities.single()
        vm.onAction(QuickEntryUiAction.Choose(ambiguity, ambiguity.candidates.first { it.id == text.market.value }))
        assertTrue(vm.uiState.value.canSave)
        vm.save()
        assertEquals(95_500L, text.balances().getValue("BBVA"))
    }

    @Test
    fun aPoolLineUsesThePoolCategoryAndLeavesTheSameProjectionsAsTheEditor() = runBlocking {
        val text = World()
        val editor = World()

        val vm = text.quickEntry().ready()
        vm.type("120 bbva perros")
        val state = withTimeout(10_000) { vm.uiState.first { it.categoryId != null } } // la sugerencia llega después de la línea
        assertEquals(text.pets.value, state.categoryId)
        assertTrue(state.categorySuggested)
        vm.save()
        editor.registerExpense(
            editor.bbva, pen(12_000), listOf(ExpensePartInput(null, pen(12_000), editor.pets, emptyList(), editor.dogs)), null, today,
        ).value()

        assertEquals(editor.balances(), text.balances())
        assertEquals(normalize(editor.lastExpense().orEmpty().map { it.toString() }), normalize(text.lastExpense().orEmpty().map { it.toString() }))
        assertEquals(editor.pools.find(editor.dogs)?.name, text.pools.find(text.dogs)?.name)
    }

    /** Los ids de las entidades son UUID distintos por mundo: se enmascaran antes de comparar. */
    private fun normalize(values: List<String>) = values.map { it.replace(Regex("[0-9a-f]{8}-[0-9a-f-]{27}"), "<id>") }
}
