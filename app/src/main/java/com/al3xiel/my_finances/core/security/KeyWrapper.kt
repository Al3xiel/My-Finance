package com.al3xiel.my_finances.core.security

/** Envuelve bytes secretos con una clave que nunca sale del almacén seguro del sistema. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray

    /** @throws IllegalStateException si no se puede desenvolver (clave perdida o datos alterados). */
    fun unwrap(wrapped: ByteArray): ByteArray

    fun deleteKey()
}
