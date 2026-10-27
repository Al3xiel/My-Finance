package com.al3xiel.my_finances.data.projection

import com.al3xiel.my_finances.domain.model.event.StoredEvent

/**
 * Mantiene tablas derivadas del registro de eventos. Determinista: el mismo flujo de eventos deja las mismas
 * filas (sin reloj ni azar); por eso `reset` + replay reconstruye todo.
 */
interface EventProjector {
    val name: String
    val version: Int

    suspend fun apply(event: StoredEvent)

    /** Borra todo lo que este proyector deriva. */
    suspend fun reset()
}
