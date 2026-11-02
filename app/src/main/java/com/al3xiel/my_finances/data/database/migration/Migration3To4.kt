package com.al3xiel.my_finances.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Ingresos esperados (v3 → v4): la tabla `expected_incomes`, vacía, con índice por estado. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS expected_incomes (id TEXT NOT NULL, income_source_id TEXT NOT NULL, " +
                "account_id TEXT NOT NULL, estimated_minor INTEGER NOT NULL, expected_from TEXT NOT NULL, " +
                "expected_to TEXT NOT NULL, repeats_monthly INTEGER NOT NULL, status TEXT NOT NULL, " +
                "cancel_reason TEXT, confirmed_movement_id TEXT, next_expected_id TEXT, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_expected_incomes_status ON expected_incomes (status)")
    }
}
