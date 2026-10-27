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
class Migration1To2Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val name = "migration-test.db"

    @Test
    fun migrates1To2KeepingPartsAndDefaultingTheNewColumns() {
        helper.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO movements (id, type, account_id, counter_account_id, amount_minor, currency, income_source_id, " +
                    "person_id, note, occurred_on, status, revision, last_sequence) " +
                    "VALUES ('m1', 'EXPENSE', 'a1', NULL, 12000, 'PEN', NULL, NULL, NULL, '2026-10-20', 'ACTIVE', 1, 5)",
            )
            execSQL("INSERT INTO movement_parts (id, movement_id, amount_minor, category_id, kind) VALUES ('p1', 'm1', 12000, 'c1', 'NORMAL')")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)

        db.query(
            "SELECT amount_minor, pool_id, pool_period_id, reimbursed_minor, reimbursement_status, created_sequence " +
                "FROM movement_parts WHERE id = 'p1'",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(12_000L, cursor.getLong(0))
            assertEquals(true, cursor.isNull(1))
            assertEquals(true, cursor.isNull(2))
            assertEquals(0L, cursor.getLong(3))
            assertEquals("PENDING", cursor.getString(4))
            // La parte conserva el orden de registro de su movimiento (last_sequence = 5).
            assertEquals(5L, cursor.getLong(5))
        }
    }

    @Test
    fun createsTheNewTables() {
        helper.createDatabase(name, 1).close()
        val db = helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2)
        val expected = setOf(
            "pools", "pool_periods", "settlement_statements", "reimbursements",
            "reimbursement_allocations", "loans", "loan_repayments",
        )
        val found = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { while (it.moveToNext()) found += it.getString(0) }
        assertEquals(emptySet<String>(), expected - found)
    }
}
