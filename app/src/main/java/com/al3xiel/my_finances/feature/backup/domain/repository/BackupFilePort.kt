package com.al3xiel.my_finances.feature.backup.domain.repository

import java.io.InputStream
import java.io.OutputStream

/**
 * El documento que el usuario eligió con el selector del sistema (SAF). Sin permiso persistente: solo vale lo que
 * dura la operación. Ningún método registra el `uri` ni su contenido.
 */
interface BackupFilePort {
    /** `null` si no se pudo abrir para escribir. */
    suspend fun openOutput(uri: String): OutputStream?

    /** `null` si no se pudo abrir para leer. */
    suspend fun openInput(uri: String): InputStream?

    /** Tamaño en bytes, o `null` si el proveedor no lo informa. */
    suspend fun sizeOf(uri: String): Long?

    /** Intenta borrar el documento; `false` si no se pudo. */
    suspend fun delete(uri: String): Boolean
}
