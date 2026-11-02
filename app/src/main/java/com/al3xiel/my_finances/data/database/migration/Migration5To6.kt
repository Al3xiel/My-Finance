package com.al3xiel.my_finances.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Trazabilidad de origen (v5 → v6): `origin_balances`, `origin_attributions` y `origin_cursor`, vacías. El proyector
 * `origin` no tiene estado guardado, así que el replay por cambio de versión (D32) las llena al arrancar.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS origin_balances (account_id TEXT NOT NULL, income_source_id TEXT NOT NULL, " +
                "amount_minor INTEGER NOT NULL, PRIMARY KEY(account_id, income_source_id))",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS origin_attributions (ledger_entry_id TEXT NOT NULL, part_id TEXT NOT NULL, " +
                "income_source_id TEXT NOT NULL, amount_minor INTEGER NOT NULL, " +
                "PRIMARY KEY(ledger_entry_id, part_id, income_source_id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_origin_attributions_income_source_id ON origin_attributions (income_source_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS origin_cursor (id INTEGER NOT NULL, last_order_key INTEGER NOT NULL, " +
                "last_sequence INTEGER NOT NULL, last_entry_id TEXT NOT NULL, PRIMARY KEY(id))",
        )
    }
}
