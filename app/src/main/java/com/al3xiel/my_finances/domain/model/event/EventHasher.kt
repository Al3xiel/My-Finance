package com.al3xiel.my_finances.domain.model.event

import java.security.MessageDigest

/** Todo lo que cubre el hash de un evento, en el orden en que entra (menos `sequence`, que lo asigna la base). */
data class HashedFields(
    val previousHash: String,
    val eventId: String,
    val type: String,
    val schemaVersion: Int,
    val aggregateType: String,
    val aggregateId: String,
    val correlationId: String,
    val payload: String,
    val occurredOn: String,
    val recordedAt: Long,
    val appVersion: String,
)

/**
 * `SHA-256` de todas las columnas del evento salvo `sequence` y `hash`, unidas con U+001F y encadenadas con el
 * hash anterior (`GENESIS` en el primero): cambiar cualquier columna a mano rompe la cadena.
 */
object EventHasher {
    const val GENESIS = "GENESIS"
    private const val SEPARATOR = '\u001F'

    fun hash(fields: HashedFields): String {
        val text = with(fields) {
            listOf(
                previousHash, eventId, type, schemaVersion, aggregateType, aggregateId,
                correlationId, payload, occurredOn, recordedAt, appVersion,
            )
        }.joinToString(SEPARATOR.toString())
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun hash(stored: StoredEvent): String = hash(
        HashedFields(
            previousHash = stored.previousHash,
            eventId = stored.eventId,
            type = stored.event.type,
            schemaVersion = stored.schemaVersion,
            aggregateType = stored.event.aggregateType.name,
            aggregateId = stored.event.aggregateId,
            correlationId = stored.correlationId,
            payload = stored.payload,
            occurredOn = stored.occurredOn.toString(),
            recordedAt = stored.recordedAt.toEpochMilli(),
            appVersion = stored.appVersion,
        ),
    )
}
