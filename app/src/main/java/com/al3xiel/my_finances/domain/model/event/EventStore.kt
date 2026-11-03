package com.al3xiel.my_finances.domain.model.event

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

/** Fila cruda con todo lo que cubre el hash: sirve para verificar y respaldar sin decodificar el payload. */
data class ChainRow(val sequence: Long, val hash: String, val fields: HashedFields)

fun StoredEvent.toChainRow() = ChainRow(
    sequence = sequence,
    hash = hash,
    fields = HashedFields(
        previousHash = previousHash,
        eventId = eventId,
        type = event.type,
        schemaVersion = schemaVersion,
        aggregateType = event.aggregateType.name,
        aggregateId = event.aggregateId,
        correlationId = correlationId,
        payload = payload,
        occurredOn = occurredOn.toString(),
        recordedAt = recordedAt.toEpochMilli(),
        appVersion = appVersion,
    ),
)

/** Primer evento y último respaldo (`BackupCreated`/`BackupRestored`), como `recorded_at` en milisegundos. */
data class BackupTimes(val firstEventAt: Long?, val lastBackupAt: Long?)

interface EventStore {
    /** Guarda los eventos en orden, encadena sus hashes y actualiza las proyecciones, todo en una transacción. */
    suspend fun append(correlationId: String, events: List<NewEvent>): List<StoredEvent>

    suspend fun readAll(): List<StoredEvent>

    suspend fun readAfter(sequence: Long): List<StoredEvent>

    suspend fun history(aggregateType: AggregateType, aggregateId: String): List<StoredEvent>

    /** Lectura por páginas sin decodificar: nunca el registro entero en memoria. */
    suspend fun readChainPage(afterSequence: Long, limit: Int): List<ChainRow> =
        readAfter(afterSequence).take(limit).map { it.toChainRow() }

    /** Eventos que no son de sembrado (D44): 0 = base sin datos propios. */
    suspend fun countNonSeed(sourceIds: List<String>, categoryIds: List<String>): Long = readAll().count { stored ->
        val e = stored.event
        !(e.type == "ProjectionsRebuilt" ||
            (e.type == "IncomeSourceCreated" && e.aggregateId in sourceIds) ||
            (e.type == "CategoryCreated" && e.aggregateId in categoryIds))
    }.toLong()

    /** Se vuelve a emitir cuando cambia el registro; el último respaldo se deriva solo de los eventos (P9). */
    fun observeBackupTimes(): Flow<BackupTimes> = flow {
        val all = readAll()
        emit(
            BackupTimes(
                all.minOfOrNull { it.recordedAt.toEpochMilli() },
                all.filter { it.event.type == "BackupCreated" || it.event.type == "BackupRestored" }
                    .maxOfOrNull { it.recordedAt.toEpochMilli() },
            ),
        )
    }
}
