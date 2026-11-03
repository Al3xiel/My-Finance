package com.al3xiel.my_finances.feature.backup.data

import com.al3xiel.my_finances.feature.backup.domain.repository.DatabaseSwapper
import java.io.File
import java.io.IOException

/**
 * Los archivos de la base viva y de la de staging, con sus auxiliares (`-wal`, `-shm`, `-journal`).
 * [move] se inyecta para poder probar un fallo en cada paso del intercambio.
 */
class DatabaseFiles(
    private val directory: File,
    private val move: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
) {
    private val live = File(directory, LIVE)
    private val staging = File(directory, STAGING)
    private val old = File(directory, "$LIVE.old")

    fun stagingFile(): File = staging

    /** Borra el staging y sus auxiliares. */
    fun deleteStaging() = deleteWithSidecars(staging)

    /** Cambia la viva por la de staging: viva → `.old`, staging → viva, borra `.old`. Si falla revierte y lanza. */
    fun swap() {
        try {
            moveWithSidecars(live, old)
            if (!move(staging, live)) throw IOException("could not move the staging database")
        } catch (e: IOException) {
            revert()
            throw e
        }
        deleteWithSidecars(old)
        deleteWithSidecars(staging)
    }

    /**
     * Al abrir la base (antes de usarla): si el proceso murió entre los dos pasos, devuelve la vieja; si ya cambió,
     * borra la vieja; un staging que sobra es de una restauración abortada.
     */
    fun recover() {
        if (!live.exists() && old.exists()) moveWithSidecars(old, live)
        if (live.exists()) deleteWithSidecars(old)
        deleteWithSidecars(staging)
    }

    private fun revert() {
        if (!live.exists() && old.exists()) moveWithSidecars(old, live)
        deleteWithSidecars(staging)
    }

    private fun moveWithSidecars(from: File, to: File) {
        if (from.exists() && !move(from, to)) throw IOException("could not move a database file")
        SIDECARS.forEach { suffix ->
            val side = File(from.path + suffix)
            if (side.exists()) move(side, File(to.path + suffix))
        }
    }

    private fun deleteWithSidecars(file: File) {
        file.delete()
        SIDECARS.forEach { File(file.path + it).delete() }
    }

    companion object {
        const val LIVE = "finances.db"
        const val STAGING = "finances-restore.db"
        private val SIDECARS = listOf("-wal", "-shm", "-journal")
    }
}

/** Cierra la base abierta y cambia su archivo por el de staging (D44). */
class FileDatabaseSwapper(
    private val files: DatabaseFiles,
    private val closeDatabase: () -> Unit,
) : DatabaseSwapper {
    override suspend fun swap() {
        closeDatabase()
        files.swap()
    }
}
