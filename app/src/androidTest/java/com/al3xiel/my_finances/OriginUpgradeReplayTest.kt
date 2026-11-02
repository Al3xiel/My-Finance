package com.al3xiel.my_finances

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.origin.OriginProjector
import com.al3xiel.my_finances.feature.reports.domain.model.OriginSourceIds
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La app que ya tenía datos recibe el proyector `origin` (base v5 → v6): sin estado guardado, la guarda de versiones lo
 * llena con un replay al arrancar, a partir del libro que ya existía.
 */
@RunWith(AndroidJUnit4::class)
class OriginUpgradeReplayTest {
    private lateinit var database: AppDatabase
    private lateinit var world: ReconciliationWorld
    private val zone = ZoneId.of("America/Lima")
    private val clock = Clock.fixed(LocalDate.of(2026, 11, 20).atTime(15, 0).atZone(zone).toInstant(), zone)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .addCallback(EventTriggersCallback)
            .build()
        world = ReconciliationWorld(database, clock, UuidIdGenerator())
    }

    @After
    fun tearDown() = database.close()

    private fun rows(sql: String): List<List<String>> = database.openHelper.readableDatabase.query(sql).use { cursor ->
        buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) }) }
    }

    @Test
    fun theVersionGuardFillsTheOriginFromTheExistingLedger() = runBlocking {
        world.wire(world.standardProjectors())
        val bbva = world.openAccount("BBVA", 100_000)
        val salary = world.source("Sueldo")
        with(world) { registerIncome(bbva, pen(20_000), salary, "Sueldo", LocalDate.of(2026, 10, 2)).value() }
        assertTrue("sin el proyector no hay origen", rows("SELECT * FROM origin_balances").isEmpty())

        world.wire(world.standardProjectors() + OriginProjector(database.originDao()))
        assertTrue("el proyector nuevo dispara el replay", world.versionGuard())

        val byAccount = rows("SELECT income_source_id, amount_minor FROM origin_balances WHERE account_id = '${bbva.value}'")
            .associate { it[0] to it[1].toLong() }
        assertEquals(mapOf(OriginSourceIds.OPENING_BALANCE to 100_000L, salary.value to 20_000L), byAccount)
        assertEquals(false, world.versionGuard())
    }
}
