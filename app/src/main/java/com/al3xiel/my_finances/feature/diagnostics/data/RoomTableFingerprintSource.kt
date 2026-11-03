package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.feature.diagnostics.domain.model.TableFingerprint
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.TableFingerprintSource
import java.security.MessageDigest
import javax.inject.Inject

/**
 * Resume cada tabla de proyección de la base real: filas y SHA-256 de sus filas ordenadas, así que el orden en que se
 * escribieron no importa. Deja fuera el registro de eventos y las tablas internas de Room.
 */
class RoomTableFingerprintSource @Inject constructor(private val database: AppDatabase) : TableFingerprintSource {
    override suspend fun fingerprints(): List<TableFingerprint> {
        val db = database.openHelper.readableDatabase
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filter { name -> EXCLUDED_PREFIXES.none { name.startsWith(it) } && name !in EXCLUDED }
        return tables.sorted().map { table ->
            val rows = ArrayList<String>()
            db.query("SELECT * FROM \"$table\"").use { cursor ->
                while (cursor.moveToNext()) {
                    rows += (0 until cursor.columnCount).joinToString(SEPARATOR) { if (cursor.isNull(it)) NULL_MARK else cursor.getString(it) }
                }
            }
            val digest = MessageDigest.getInstance("SHA-256")
            rows.sorted().forEach { digest.update(it.toByteArray(Charsets.UTF_8)); digest.update(0) }
            TableFingerprint(table, rows.size, digest.digest().joinToString("") { "%02x".format(it) }.take(HASH_CHARS))
        }
    }

    private companion object {
        const val SEPARATOR = "\u001f"
        const val NULL_MARK = "\u0000null"
        const val HASH_CHARS = 16
        val EXCLUDED = setOf("events", "projection_state", "android_metadata")
        val EXCLUDED_PREFIXES = listOf("sqlite_", "room_")
    }
}
