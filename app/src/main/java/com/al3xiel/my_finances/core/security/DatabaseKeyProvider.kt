package com.al3xiel.my_finances.core.security

import java.io.File
import java.security.SecureRandom

/**
 * Clave de 256 bits de la base. En disco solo vive envuelta (en `noBackupFilesDir`); nunca en claro.
 * Si la clave guardada no se puede leer no se regenera: perdería la base sin avisar.
 */
class DatabaseKeyProvider(
    private val keyWrapper: KeyWrapper,
    private val keyFile: File,
    private val random: SecureRandom,
) {
    @Synchronized
    fun getOrCreate(): ByteArray {
        if (keyFile.exists()) return keyWrapper.unwrap(keyFile.readBytes())
        val key = ByteArray(KEY_BYTES).also(random::nextBytes)
        val temp = File(keyFile.parentFile, "${keyFile.name}.tmp")
        temp.writeBytes(keyWrapper.wrap(key))
        check(temp.renameTo(keyFile)) { "cannot persist the database key" }
        return key
    }

    @Synchronized
    fun delete() {
        keyFile.delete()
        File(keyFile.parentFile, "${keyFile.name}.tmp").delete()
        keyWrapper.deleteKey()
    }

    private companion object {
        const val KEY_BYTES = 32
    }
}
