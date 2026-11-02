package com.al3xiel.my_finances

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.domain.model.IncomeSourceKind
import com.al3xiel.my_finances.domain.model.MovementType
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AdjustmentDirection
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.usecase.ChainVerification
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * El flujo F5 de `docs/05` de punta a punta, con casos de uso reales, registro de eventos real y Room real:
 * el banco dice S/ 812.40 y la app S/ 800.00 → se registra (no ajusta) → un reembolso olvidado se registra vinculado
 * → resolver Desconocida con un ingreso no identificado de S/ 2.40. Una anulación posterior no vinculada no cambia la
 * diferencia y una corrección vinculada no duplica. Tras el replay las proyecciones son idénticas, fila por fila.
 */
@RunWith(AndroidJUnit4::class)
class ReconciliationTraceReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var world: ReconciliationWorld

    private val zone = ZoneId.of("America/Lima")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-02T20:00:00Z"), zone)
    private val ids = UuidIdGenerator()
    private val day = LocalDate.of(2026, 11, 1)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        world = ReconciliationWorld(database, clock, ids)
        world.wire(world.standardProjectors())
    }

    @After
    fun tearDown() = database.close()

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
        "reconciliations" to dump("reconciliations", "id"),
        "reconciliation_links" to dump("reconciliation_links", "reconciliation_id, movement_id"),
    )

    @Test
    fun f5BankSays81240AppSays80000ForgottenRefundThenUnknownAdjustmentOf240AndReplaysIdentically() = runBlocking {
        with(world) {
            // La app dice S/ 800.00: saldo inicial S/ 750.00 + un ingreso de S/ 50.00 anterior al chequeo.
            val bcp = openAccount("BCP", 75_000)
            val sueldo = source("Sueldo")
            val earlier = registerIncome(bcp, pen(5_000), sueldo, "ingreso previo", day).value()
            assertEquals(80_000L, balance("BCP"))

            // El banco dice S/ 812.40: registrar no mueve ningún saldo (C1).
            val id = record(bcp, pen(81_240)).value()
            assertEquals(80_000L, balance("BCP"))
            assertEquals(pen(1_240), difference(id))
            assertEquals(pen(80_000), reconciliations.find(id)?.snapshotComputed)

            // Una anulación posterior NO vinculada no cambia la diferencia: el chequeo congela lo que la app sabía.
            void(earlier, "duplicado", confirmNegativeBalance = true).value()
            assertEquals(75_000L, balance("BCP"))
            assertEquals(pen(1_240), difference(id))

            // "Me olvidé un reembolso de S/ 10": se registra vinculado (S/ 9.00 mal tecleado) y la diferencia baja en vivo.
            val refund = registerIncome(bcp, pen(900), sueldo, "reembolso olvidado", day, id).value()
            assertEquals(pen(340), difference(id))
            assertEquals(listOf(refund), reconciliations.observeDetail(id).first()!!.linked.map { it.id })

            // Una corrección vinculada reemplaza el asiento viejo: no se cuenta dos veces.
            correct(refund, MovementDraft.Income(bcp, pen(1_000), sueldo, "reembolso olvidado", day), "monto", reconciliationId = id).value()
            assertEquals(pen(240), difference(id))
            assertEquals(pen(81_000), reconciliations.observeDetail(id).first()!!.computed)

            // Resolver: causa Desconocida → ingreso no identificado de S/ 2.40 con su fuente aislada (C5).
            resolve(id, ReconciliationCause.UNKNOWN, "no encontré el origen").value()
            val resolved = reconciliations.find(id)!!
            assertEquals(ReconciliationStatus.RESOLVED, resolved.status)
            assertEquals(AdjustmentDirection.UNIDENTIFIED_INCOME, resolved.adjustment?.direction)
            assertEquals(pen(240), resolved.adjustment?.amount)
            assertFalse(resolved.needsReview)

            val adjustment = database.movementDao().movement(resolved.adjustment!!.movementId.value)!!
            assertEquals(MovementType.ADJUSTMENT, adjustment.type)
            assertEquals(day.plusDays(1).toString(), adjustment.occurredOn)
            val unknownSource = SystemCatalog.incomeSources.first { it.kind == IncomeSourceKind.UNKNOWN_ADJUSTMENT }.id.value
            assertEquals(unknownSource, adjustment.incomeSourceId)
            assertEquals(75_000L + 1_000L + 240L, balance("BCP"))
        }
        assertReplayLeavesTheSameProjections()
    }

    @Test
    fun cancellingLeavesNoAdjustmentAndLinksHaveNoEffectOnBalances() = runBlocking {
        with(world) {
            val bcp = openAccount("BCP", 80_000)
            val sueldo = source("Sueldo")
            val id = record(bcp, pen(81_240)).value()
            registerIncome(bcp, pen(1_000), sueldo, null, day, id).value()
            val balanceBefore = balance("BCP")

            com.al3xiel.my_finances.feature.reconciliation.domain.usecase.CancelReconciliationUseCase(
                reconciliations,
                com.al3xiel.my_finances.domain.usecase.EventRecorder(store, ids, clock),
            )(id, "me equivoqué de cuenta").value()

            assertEquals(ReconciliationStatus.CANCELLED, reconciliations.find(id)?.status)
            assertEquals(balanceBefore, balance("BCP"))
            assertEquals(0, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM movements WHERE type = 'ADJUSTMENT'").use { it.moveToFirst(); it.getInt(0) })
        }
        assertReplayLeavesTheSameProjections()
    }

    private suspend fun assertReplayLeavesTheSameProjections() {
        val eventsBefore = world.store.readAll().size
        assertEquals(ChainVerification.Valid(eventsBefore), VerifyEventChainUseCase(world.store)())
        val before = projections()

        // RESTORE no re-verifica conciliaciones: solo reconstruye.
        world.replay(RebuildReason.RESTORE)

        val after = projections()
        before.forEach { (table, rows) -> assertEquals("tabla $table", rows, after[table]) }
        assertEquals(before, after)
        assertEquals(eventsBefore + 1, world.store.readAll().size)
        assertEquals(ChainVerification.Valid(eventsBefore + 1), VerifyEventChainUseCase(world.store)())
        listOf("catalog", "movement", "reconciliation").forEach {
            assertNotNull("proyector $it", database.projectionStateDao().state(it)?.rebuiltAt)
        }
    }
}
