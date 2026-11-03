package com.al3xiel.my_finances.feature.diagnostics.data.workspace

import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.core.security.KeyWrapper
import java.io.File
import java.security.SecureRandom

/**
 * Clave de la base del espacio y sellado de sus archivos pequeños (D51). Usa su propio alias de Keystore y su propio archivo
 * de clave: borrar el espacio nunca toca la clave de la base real.
 */
class WorkspaceKeys(
    private val wrapper: KeyWrapper,
    keyFile: File,
    random: SecureRandom,
) {
    private val provider = DatabaseKeyProvider(wrapper, keyFile, random)

    fun databaseKey(): ByteArray = provider.getOrCreate()

    fun seal(plain: ByteArray): ByteArray = wrapper.wrap(plain)

    /** @throws IllegalStateException si el archivo está alterado o la clave se perdió. */
    fun unseal(sealed: ByteArray): ByteArray = wrapper.unwrap(sealed)

    /** Borra el archivo de clave y el alias; deja sin sentido cualquier cosa que siga sellada con ellos. */
    fun delete() = provider.delete()
}
