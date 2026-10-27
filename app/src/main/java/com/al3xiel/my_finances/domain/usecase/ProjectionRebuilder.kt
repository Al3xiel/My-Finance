package com.al3xiel.my_finances.domain.usecase

/** Borra las proyecciones y las reconstruye desde el registro de eventos, en una sola transacción. */
interface ProjectionRebuilder {
    /** Versión de cada proyector tras reconstruir. */
    suspend fun rebuild(): Map<String, Int>
}
