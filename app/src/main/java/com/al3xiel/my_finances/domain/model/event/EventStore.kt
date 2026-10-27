package com.al3xiel.my_finances.domain.model.event

import java.time.Instant
import java.time.LocalDate

/** Un hecho por guardar: `occurredOn` es cuándo pasó en la vida real, no cuándo se registra. */
data class NewEvent(val event: DomainEvent, val occurredOn: LocalDate)

/** Fila del registro de eventos. `payload` es el texto exacto que cubre el hash. */
data class StoredEvent(
    val sequence: Long,
    val eventId: String,
    val schemaVersion: Int,
    val correlationId: String,
    val payload: String,
    val occurredOn: LocalDate,
    val recordedAt: Instant,
    val appVersion: String,
    val previousHash: String,
    val hash: String,
    val event: DomainEvent,
)

interface EventStore {
    /** Guarda los eventos en orden, encadena sus hashes y actualiza las proyecciones, todo en una transacción. */
    suspend fun append(correlationId: String, events: List<NewEvent>): List<StoredEvent>

    suspend fun readAll(): List<StoredEvent>

    suspend fun readAfter(sequence: Long): List<StoredEvent>

    suspend fun history(aggregateType: AggregateType, aggregateId: String): List<StoredEvent>
}
