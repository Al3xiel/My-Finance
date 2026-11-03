package com.al3xiel.my_finances.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Cúmulos, rendiciones, reembolsos y deudas (v1 → v2). Las filas de `movement_parts` quedan con los valores por defecto. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE movement_parts ADD COLUMN pool_id TEXT")
        connection.execSQL("ALTER TABLE movement_parts ADD COLUMN pool_period_id TEXT")
        connection.execSQL("ALTER TABLE movement_parts ADD COLUMN reimbursed_minor INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE movement_parts ADD COLUMN reimbursement_status TEXT NOT NULL DEFAULT 'PENDING'")
        connection.execSQL("ALTER TABLE movement_parts ADD COLUMN created_sequence INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_movement_parts_pool_id ON movement_parts (pool_id)")
        // Orden de registro de las partes que ya existían: la secuencia del último evento de su movimiento (la de su
        // creación si nunca se corrigió). Un replay la deja exacta; mientras tanto el desempate de OLDEST_FIRST ya
        // sigue el orden en que se registraron.
        connection.execSQL(
            "UPDATE movement_parts SET created_sequence = COALESCE(" +
                "(SELECT m.last_sequence FROM movements m WHERE m.id = movement_parts.movement_id), 0)",
        )

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS pools (id TEXT NOT NULL, name TEXT NOT NULL, responsible_person_id TEXT NOT NULL, " +
                "kind TEXT NOT NULL, default_category_id TEXT, default_tag_ids TEXT NOT NULL, is_archived INTEGER NOT NULL, " +
                "PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_pools_responsible_person_id ON pools (responsible_person_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS pool_periods (id TEXT NOT NULL, pool_id TEXT NOT NULL, opened_on TEXT NOT NULL, " +
                "closed_on TEXT, status TEXT NOT NULL, statement_id TEXT, total_minor INTEGER NOT NULL, " +
                "reimbursed_minor INTEGER NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_pool_periods_pool_id ON pool_periods (pool_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS settlement_statements (id TEXT NOT NULL, pool_id TEXT NOT NULL, period_id TEXT NOT NULL, " +
                "pending_total_minor INTEGER NOT NULL, format TEXT NOT NULL, generated_on TEXT NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_statements_pool_id ON settlement_statements (pool_id)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_statements_period_id ON settlement_statements (period_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS reimbursements (id TEXT NOT NULL, person_id TEXT NOT NULL, movement_id TEXT NOT NULL, " +
                "amount_minor INTEGER NOT NULL, strategy TEXT NOT NULL, occurred_on TEXT NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_reimbursements_person_id ON reimbursements (person_id)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_reimbursements_movement_id ON reimbursements (movement_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS reimbursement_allocations (reimbursement_id TEXT NOT NULL, part_id TEXT NOT NULL, " +
                "amount_minor INTEGER NOT NULL, PRIMARY KEY(reimbursement_id, part_id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_reimbursement_allocations_part_id ON reimbursement_allocations (part_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS loans (id TEXT NOT NULL, person_id TEXT NOT NULL, movement_id TEXT NOT NULL, " +
                "principal_minor INTEGER NOT NULL, repaid_minor INTEGER NOT NULL, status TEXT NOT NULL, " +
                "occurred_on TEXT NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_loans_person_id ON loans (person_id)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_loans_movement_id ON loans (movement_id)")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS loan_repayments (id TEXT NOT NULL, loan_id TEXT NOT NULL, movement_id TEXT NOT NULL, " +
                "amount_minor INTEGER NOT NULL, occurred_on TEXT NOT NULL, PRIMARY KEY(id))",
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_loan_repayments_loan_id ON loan_repayments (loan_id)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS index_loan_repayments_movement_id ON loan_repayments (movement_id)")
    }
}
