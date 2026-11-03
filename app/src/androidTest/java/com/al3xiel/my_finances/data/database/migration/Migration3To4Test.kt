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
class Migration3To4Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val name = "migration-expected-income-test.db"

    @Test
    fun migrates3To4KeepingTheExistingRows() {
        helper.createDatabase(name, 3).apply {
            execSQL(
                "INSERT INTO funds (id, name, consumption_mode, daily_estimate_minor, starts_on, ends_on, allocated_minor, " +
                    "untracked_spent_minor, status, remainder_policy, rollover_to_fund_id, closed_on) " +
                    "VALUES ('f1', 'Pasajes', 'NONE', NULL, '2026-10-01', NULL, 5000, 0, 'ACTIVE', NULL, NULL, NULL)",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4)

        db.query("SELECT allocated_minor FROM funds WHERE id = 'f1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals(5_000L, cursor.getLong(0))
        }
    }

    @Test
    fun createsTheExpectedIncomesTableEmptyAndAcceptsARow() {
        helper.createDatabase(name, 3).close()

        val db = helper.runMigrationsAndValidate(name, 4, true, MIGRATION_3_4)

        db.query("SELECT COUNT(*) FROM expected_incomes").use { cursor ->
            cursor.moveToFirst()
            assertEquals(0, cursor.getInt(0))
        }
        db.execSQL(
            "INSERT INTO expected_incomes (id, income_source_id, account_id, estimated_minor, expected_from, expected_to, " +
                "repeats_monthly, status, cancel_reason, confirmed_movement_id, next_expected_id) " +
                "VALUES ('e1', 's1', 'a1', 200000, '2026-10-28', '2026-11-05', 1, 'SCHEDULED', NULL, NULL, NULL)",
        )
        db.query("SELECT status FROM expected_incomes WHERE id = 'e1'").use { cursor ->
            cursor.moveToFirst()
            assertEquals("SCHEDULED", cursor.getString(0))
        }
    }
}
