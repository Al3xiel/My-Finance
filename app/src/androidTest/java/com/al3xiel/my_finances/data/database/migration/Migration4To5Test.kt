package com.al3xiel.my_finances.data.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val name = "migration-reconciliation-test.db"

    @Test
    fun migrates4To5KeepingTheLedgerAndLeavingVoidedSequenceNull() {
        helper.createDatabase(name, 4).apply {
            execSQL(
                "INSERT INTO ledger_entries (id, sequence, movement_id, account_id, delta_minor, entry_kind, occurred_on, is_voided) " +
                    "VALUES ('1-0', 1, NULL, 'a1', 80000, 'OPENING', '2026-10-01', 0), " +
                    "('2-0', 2, 'm1', 'a1', -500, 'EXPENSE', '2026-10-02', 1)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5)

        db.query("SELECT id, delta_minor, is_voided, voided_sequence FROM ledger_entries ORDER BY sequence").use { cursor ->
            assertEquals(2, cursor.count)
            cursor.moveToFirst()
            assertEquals(80_000L, cursor.getLong(1))
            assertTrue(cursor.isNull(3))
            cursor.moveToNext()
            assertEquals(1, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
        }
    }

    @Test
    fun createsTheReconciliationTablesEmptyAndAcceptsRows() {
        helper.createDatabase(name, 4).close()

        val db = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5)

        db.query("SELECT COUNT(*) FROM reconciliations").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.execSQL(
            "INSERT INTO reconciliations (id, account_id, currency, real_minor, snapshot_computed_minor, checked_sequence, status, " +
                "cause, adjustment_movement_id, adjustment_minor, cancel_reason, checked_at, resolved_at, needs_review) " +
                "VALUES ('r1', 'a1', 'PEN', 81240, 80000, 2, 'OPEN', NULL, NULL, NULL, NULL, '2026-10-25T10:00:00Z', NULL, 0)",
        )
        db.execSQL("INSERT INTO reconciliation_links (reconciliation_id, movement_id) VALUES ('r1', 'm1')")
        db.query("SELECT status FROM reconciliations WHERE id = 'r1'").use {
            it.moveToFirst()
            assertEquals("OPEN", it.getString(0))
        }
    }
}
