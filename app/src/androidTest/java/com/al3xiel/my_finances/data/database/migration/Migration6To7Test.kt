package com.al3xiel.my_finances.data.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.data.database.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val name = "migration-part-note-test.db"

    private val insertPart =
        "INSERT INTO movement_parts (id, movement_id, amount_minor, category_id, kind, pool_id, pool_period_id, reimbursed_minor, " +
            "reimbursement_status, created_sequence, fund_id) VALUES ('%s', 'm1', %d, 'food', 'NORMAL', NULL, NULL, 0, 'PENDING', 1, NULL)"

    @Test
    fun everyExistingPartIsKeptWithNoDetail() {
        helper.createDatabase(name, 6).apply {
            execSQL(insertPart.format("p1", 300))
            execSQL(insertPart.format("p2", 200))
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7)

        db.query("SELECT id, amount_minor, note FROM movement_parts ORDER BY id").use {
            assertEquals(2, it.count)
            it.moveToFirst()
            assertEquals("p1", it.getString(0))
            assertEquals(300L, it.getLong(1))
            assertNull(it.getString(2))
            it.moveToNext()
            assertEquals("p2", it.getString(0))
            assertNull(it.getString(2))
        }
    }

    @Test
    fun aMigratedPartAcceptsADetail() {
        helper.createDatabase(name, 6).apply {
            execSQL(insertPart.format("p1", 300))
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7)
        db.execSQL("UPDATE movement_parts SET note = 'Pastillas de Boby' WHERE id = 'p1'")

        db.query("SELECT note FROM movement_parts WHERE id = 'p1'").use {
            it.moveToFirst()
            assertEquals("Pastillas de Boby", it.getString(0))
        }
    }
}
