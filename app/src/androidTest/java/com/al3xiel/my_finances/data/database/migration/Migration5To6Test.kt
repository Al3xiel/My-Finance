package com.al3xiel.my_finances.data.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration5To6Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val name = "migration-origin-test.db"

    @Test
    fun keepsTheLedgerAndCreatesTheOriginTablesEmpty() {
        helper.createDatabase(name, 5).apply {
            execSQL(
                "INSERT INTO ledger_entries (id, sequence, movement_id, account_id, delta_minor, entry_kind, occurred_on, is_voided, voided_sequence) " +
                    "VALUES ('1-0', 1, NULL, 'a1', 80000, 'OPENING', '2026-10-01', 0, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6)

        db.query("SELECT delta_minor FROM ledger_entries").use {
            it.moveToFirst()
            assertEquals(80_000L, it.getLong(0))
        }
        listOf("origin_balances", "origin_attributions", "origin_cursor").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use {
                it.moveToFirst()
                assertEquals(table, 0, it.getInt(0))
            }
        }
    }

    @Test
    fun theNewTablesAcceptRowsAndEnforceTheirKeys() {
        helper.createDatabase(name, 5).close()
        val db = helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6)

        db.execSQL("INSERT INTO origin_balances (account_id, income_source_id, amount_minor) VALUES ('a1', 's1', 500)")
        db.execSQL("INSERT INTO origin_attributions (ledger_entry_id, part_id, income_source_id, amount_minor) VALUES ('2-0', '', 's1', 20)")
        db.execSQL("INSERT INTO origin_cursor (id, last_order_key, last_sequence, last_entry_id) VALUES (1, 2, 2, '2-0')")
        val duplicate = runCatching {
            db.execSQL("INSERT INTO origin_balances (account_id, income_source_id, amount_minor) VALUES ('a1', 's1', 1)")
        }
        assertEquals(true, duplicate.isFailure)
    }
}
