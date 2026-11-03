package com.al3xiel.my_finances.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Fondos (v2 → v3): las cuatro tablas del fondo y `movement_parts.fund_id`. Las partes que ya existían quedan sin fondo. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE movement_parts ADD COLUMN fund_id TEXT")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_movement_parts_fund_id ON movement_parts (fund_id)")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS funds (id TEXT NOT NULL, name TEXT NOT NULL, consumption_mode TEXT NOT NULL, " +
                "daily_estimate_minor INTEGER, starts_on TEXT NOT NULL, ends_on TEXT, allocated_minor INTEGER NOT NULL, " +
                "untracked_spent_minor INTEGER NOT NULL, status TEXT NOT NULL, remainder_policy TEXT, " +
                "rollover_to_fund_id TEXT, closed_on TEXT, PRIMARY KEY(id))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS fund_usage_days (fund_id TEXT NOT NULL, day TEXT NOT NULL, PRIMARY KEY(fund_id, day))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS fund_counts (id TEXT NOT NULL, fund_id TEXT NOT NULL, declared_remaining_minor INTEGER NOT NULL, " +
                "estimated_spent_minor INTEGER, real_spent_minor INTEGER NOT NULL, deviation_minor INTEGER, " +
                "occurred_on TEXT NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_fund_counts_fund_id ON fund_counts (fund_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS fund_topups (id TEXT NOT NULL, fund_id TEXT NOT NULL, amount_minor INTEGER NOT NULL, " +
                "linked_income_movement_id TEXT, occurred_on TEXT NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_fund_topups_fund_id ON fund_topups (fund_id)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_fund_topups_linked_income_movement_id ON fund_topups (linked_income_movement_id)",
        )
    }
}
