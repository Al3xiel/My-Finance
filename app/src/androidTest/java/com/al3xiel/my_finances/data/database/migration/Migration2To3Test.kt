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
class Migration2To3Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val name = "migration-fund-test.db"

    @Test
    fun migrates2To3KeepingPartsWithNoFund() {
        helper.createDatabase(name, 2).apply {
            execSQL(
                "INSERT INTO movements (id, type, account_id, counter_account_id, amount_minor, currency, income_source_id, " +
                    "person_id, note, occurred_on, status, revision, last_sequence) " +
                    "VALUES ('m1', 'EXPENSE', 'a1', NULL, 12000, 'PEN', NULL, NULL, NULL, '2026-10-20', 'ACTIVE', 1, 5)",
            )
            execSQL(
                "INSERT INTO movement_parts (id, movement_id, amount_minor, category_id, kind, pool_id, pool_period_id, " +
                    "reimbursed_minor, reimbursement_status, created_sequence) " +
                    "VALUES ('p1', 'm1', 12000, 'c1', 'NORMAL', 'pool1', 'per1', 4000, 'PARTIALLY_REIMBURSED', 5)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)

        db.query("SELECT amount_minor, pool_id, reimbursed_minor, reimbursement_status, created_sequence, fund_id FROM movement_parts WHERE id = 'p1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(12_000L, cursor.getLong(0))
            assertEquals("pool1", cursor.getString(1))
            assertEquals(4_000L, cursor.getLong(2))
            assertEquals("PARTIALLY_REIMBURSED", cursor.getString(3))
            assertEquals(5L, cursor.getLong(4))
            assertEquals(true, cursor.isNull(5))
        }
        db.query("SELECT COUNT(*) FROM movements").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun createsTheFundTablesEmpty() {
        helper.createDatabase(name, 2).close()

        val db = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)

        listOf("funds", "fund_usage_days", "fund_counts", "fund_topups").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals(table, 0, cursor.getInt(0))
            }
        }
    }
}
