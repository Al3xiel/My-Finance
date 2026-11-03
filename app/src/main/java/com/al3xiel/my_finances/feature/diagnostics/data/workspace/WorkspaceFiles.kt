package com.al3xiel.my_finances.feature.diagnostics.data.workspace

import java.io.File

/**
 * Los archivos del espacio de diagnóstico, todos en la carpeta de bases: la base con sus auxiliares (`-wal`, `-shm`, `-journal`)
 * y los dos archivos pequeños sellados con Keystore (el paquete abierto y el diccionario aplicado). Nunca toca `finances.db`.
 */
class WorkspaceFiles(private val directory: File) {
    val database = File(directory, DATABASE)
    val metaFile = File(directory, META)
    val dictionaryFile = File(directory, DICTIONARY)

    /** Hay algo guardado: un paquete abierto o, al menos, restos de su base. */
    fun occupied(): Boolean = metaFile.exists() || database.exists()

    /** Escribe en un archivo temporal y lo renombra, para no dejar un archivo sellado a medias. */
    fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(directory, target.name + TEMP)
        temp.writeBytes(bytes)
        check(temp.renameTo(target) || (target.delete() && temp.renameTo(target))) { "cannot persist a workspace file" }
    }

    fun deleteAll() {
        listOf(database, metaFile, dictionaryFile).forEach { file ->
            file.delete()
            File(directory, file.name + TEMP).delete()
            SIDECARS.forEach { File(directory, file.name + it).delete() }
        }
    }

    private companion object {
        const val DATABASE = "diagnostic-workspace.db"
        const val META = "diagnostic-workspace.meta"
        const val DICTIONARY = "diagnostic-workspace.dict"
        const val TEMP = ".tmp"
        val SIDECARS = listOf("-wal", "-shm", "-journal")
    }
}
