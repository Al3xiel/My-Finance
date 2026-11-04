package com.al3xiel.my_finances.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Detalle de cada parte del gasto (v6 → v7): `movement_parts.note`, nula. Las filas que ya existen quedan sin detalle, que es
 * justo lo que reproduciría el replay (los eventos anteriores no lo llevan), así que no hace falta reconstruir.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE movement_parts ADD COLUMN note TEXT")
    }
}
