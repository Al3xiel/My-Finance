package com.al3xiel.my_finances.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Conciliación (v4 → v5): `ledger_entries.voided_sequence` (nulo en lo ya anulado: el replay por cambio de versión lo
 * reconstruye) y las tablas `reconciliations` y `reconciliation_links`, vacías.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE ledger_entries ADD COLUMN voided_sequence INTEGER")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS reconciliations (id TEXT NOT NULL, account_id TEXT NOT NULL, currency TEXT NOT NULL, " +
                "real_minor INTEGER NOT NULL, snapshot_computed_minor INTEGER NOT NULL, checked_sequence INTEGER NOT NULL, " +
                "status TEXT NOT NULL, cause TEXT, adjustment_movement_id TEXT, adjustment_minor INTEGER, cancel_reason TEXT, " +
                "checked_at TEXT NOT NULL, resolved_at TEXT, needs_review INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_reconciliations_account_id_status ON reconciliations (account_id, status)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS reconciliation_links (reconciliation_id TEXT NOT NULL, movement_id TEXT NOT NULL, " +
                "PRIMARY KEY(reconciliation_id, movement_id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_reconciliation_links_movement_id ON reconciliation_links (movement_id)")
    }
}
