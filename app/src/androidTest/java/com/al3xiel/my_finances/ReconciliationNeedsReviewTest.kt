package com.al3xiel.my_finances

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.EventProjector
import com.al3xiel.my_finances.data.projection.movement.MovementProjector
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import com.al3xiel.my_finances.domain.model.event.RebuildReason
import com.al3xiel.my_finances.domain.model.event.ReconciliationCause
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.movements.domain.model.MovementDraft
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * `MovementProjector` como lo dejaría una versión nueva con un error: a partir de `version` ignora los ingresos. Es la forma
 * de probar con Room real que subir la versión de un proyector hace que el arranque reconstruya (D32) y que la conciliación
 * descubra, con la evidencia de sus eventos, lo que ya no cuadra (D31).
 */
private class MovementProjectorThatMayDropIncomes(
    private val delegate: MovementProjector,
    override val version: Int,
    private val dropIncomes: Boolean,
) : EventProjector {
    override val name get() = delegate.name
    override val order get() = delegate.order

    override suspend fun apply(event: StoredEvent) {
        if (dropIncomes && event.event is IncomeRegistered) return
        delegate.apply(event)
    }

    override suspend fun reset() = delegate.reset()
}

/**
 * Re-verificación de conciliaciones (C6, D31, D32), de punta a punta:
 * - una `BALANCED` que ya no cuadra y un ajuste que ya no corresponde se marcan `needsReview` solo cuando una versión
 *   nueva de un proyector dispara el replay `VERSION_CHANGE` al arrancar;
 * - un replay `RESTORE`, un evento normal o una corrección no marcan nada;
 * - anular el ajuste limpia la marca y la conciliación sigue `RESOLVED`.
 */
@RunWith(AndroidJUnit4::class)
class ReconciliationNeedsReviewTest {
    private lateinit var database: AppDatabase
    private lateinit var world: ReconciliationWorld

    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-02T20:00:00Z"), ZoneId.of("America/Lima"))
    private val ids = UuidIdGenerator()
    private val day = LocalDate.of(2026, 11, 1)

    private var balancedId = ReconciliationId("")
    private var adjustedId = ReconciliationId("")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        world = ReconciliationWorld(database, clock, ids)
    }

    @After
    fun tearDown() = database.close()

    /** El código de hoy (versión 4 del proyector de movimientos) o el de la versión siguiente, con el error. */
    private fun projectors(newVersion: Boolean): Set<EventProjector> {
        val movement = MovementProjector(database.movementDao(), database.ledgerDao())
        val standard = world.standardProjectors().filterNot { it.name == movement.name }
        val wrapped = MovementProjectorThatMayDropIncomes(
            movement, version = if (newVersion) movement.version + 1 else movement.version, dropIncomes = newVersion,
        )
        return standard.toSet() + wrapped
    }

    private suspend fun needsReview(id: ReconciliationId) = world.reconciliations.find(id)!!.needsReview

    /** BCP cuadra (`BALANCED`) y BBVA se resolvió con un ajuste de S/ 2.40; ambos dependen de un ingreso anterior al chequeo. */
    private suspend fun resolveBoth() = with(world) {
        val sueldo = source("Sueldo")
        val bcp = openAccount("BCP", 80_000)
        registerIncome(bcp, pen(1_000), sueldo, null, day).value()
        balancedId = record(bcp, pen(81_000)).value()
        resolve(balancedId, ReconciliationCause.BALANCED, null).value()

        val bbva = openAccount("BBVA", 50_000)
        registerIncome(bbva, pen(500), sueldo, null, day).value()
        adjustedId = record(bbva, pen(50_740)).value()
        resolve(adjustedId, ReconciliationCause.UNKNOWN, null).value()
    }

    @Test
    fun aNewProjectorVersionReplaysAtStartupAndMarksOnlyWhatNoLongerAddsUp() = runBlocking {
        world.wire(projectors(newVersion = false))
        resolveBoth()
        assertFalse(needsReview(balancedId))
        assertFalse(needsReview(adjustedId))
        assertEquals(false, world.versionGuard()) // mismas versiones: el arranque no hace nada

        // La app se actualiza: el proyector sube de versión y su cálculo cambió.
        world.wire(projectors(newVersion = true))
        assertTrue(world.versionGuard())

        val last = world.store.readAll().last().event as ProjectionsRebuilt
        assertEquals(RebuildReason.VERSION_CHANGE, last.reason)
        assertTrue("BALANCED que ya no cuadra", needsReview(balancedId))
        assertTrue("ajuste que ya no corresponde", needsReview(adjustedId))
        assertEquals(ReconciliationStatus.RESOLVED, world.reconciliations.find(balancedId)!!.status)

        // Ya al día: un segundo arranque no vuelve a reconstruir.
        assertEquals(false, world.versionGuard())
    }

    @Test
    fun anOrdinaryReplayAnEventOrACorrectionNeverMark() = runBlocking {
        world.wire(projectors(newVersion = false))
        resolveBoth()

        // Un ingreso y su corrección posteriores (no vinculados) no cambian lo que la conciliación vio al chequear.
        with(world) {
            val bcp = accountId("BCP")
            val other = source("Otra")
            val income = registerIncome(bcp, pen(300), other, null, day).value()
            correct(income, MovementDraft.Income(bcp, pen(400), other, null, day), "monto").value()
        }
        assertFalse(needsReview(balancedId))
        assertFalse(needsReview(adjustedId))

        // Restaurar un respaldo reconstruye pero no re-verifica.
        world.replay(RebuildReason.RESTORE)
        assertFalse(needsReview(balancedId))
        assertFalse(needsReview(adjustedId))
    }

    @Test
    fun voidingTheAdjustmentClearsTheMarkAndKeepsItResolved() = runBlocking {
        world.wire(projectors(newVersion = false))
        resolveBoth()
        world.wire(projectors(newVersion = true))
        world.versionGuard()
        assertTrue(needsReview(adjustedId))

        val adjustment = world.reconciliations.find(adjustedId)!!.adjustment!!
        with(world) { void(adjustment.movementId, "ya no corresponde", confirmNegativeBalance = true).value() }

        val after = world.reconciliations.find(adjustedId)!!
        assertEquals(ReconciliationStatus.RESOLVED, after.status)
        assertFalse(after.needsReview)
        assertTrue("la otra sigue marcada", needsReview(balancedId))

        // Una re-verificación manual (S73) no vuelve a marcar el ajuste ya anulado.
        world.replay(RebuildReason.MANUAL)
        assertFalse(needsReview(adjustedId))
        assertTrue(needsReview(balancedId))
    }
}
