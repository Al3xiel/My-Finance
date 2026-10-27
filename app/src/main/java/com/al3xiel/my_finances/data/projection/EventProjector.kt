package com.al3xiel.my_finances.data.projection

import com.al3xiel.my_finances.domain.model.event.StoredEvent

/**
 * Mantiene tablas derivadas del registro de eventos. Determinista: el mismo flujo de eventos deja las mismas
 * filas (sin reloj ni azar); por eso `reset` + replay reconstruye todo.
 */
interface EventProjector {
    val name: String
    val version: Int

    /**
     * Orden de aplicación, igual en vivo y en el replay: los que leen columnas que escribe otro proyector van después
     * (por ejemplo, reembolsos y deudas leen las partes que deja el de movimientos).
     */
    val order: Int get() = 0

    suspend fun apply(event: StoredEvent)

    /** Borra todo lo que este proyector deriva. */
    suspend fun reset()
}
