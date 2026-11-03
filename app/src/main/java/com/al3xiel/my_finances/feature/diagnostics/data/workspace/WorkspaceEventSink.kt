package com.al3xiel.my_finances.feature.diagnostics.data.workspace

import androidx.room.withTransaction
import com.al3xiel.my_finances.data.eventstore.EventEntity
import com.al3xiel.my_finances.feature.diagnostics.data.PackageEventLine
import javax.inject.Inject

/** Escribe los eventos de un paquete ya validado en el espacio. */
fun interface WorkspaceEventWriter {
    suspend fun write(events: List<PackageEventLine>)
}

/**
 * Inserta las filas con su `sequence`, hashes y `recorded_at` originales, en una transacción y sin encadenar de nuevo ni
 * proyectar: la cadena del paquete no se verifica (viene anonimizada) y la re-proyección es un paso aparte.
 */
class WorkspaceEventSink @Inject constructor(private val workspace: DiagnosticWorkspace) : WorkspaceEventWriter {
    override suspend fun write(events: List<PackageEventLine>) {
        val database = workspace.database()
        database.withTransaction {
            events.chunked(CHUNK).forEach { chunk -> database.eventDao().insertRaw(chunk.map { it.toEntity() }) }
        }
    }

    private fun PackageEventLine.toEntity() = EventEntity(
        sequence = sequence,
        eventId = eventId,
        type = type,
        schemaVersion = schemaVersion,
        aggregateType = aggregateType,
        aggregateId = aggregateId,
        correlationId = correlationId,
        payload = payload,
        occurredOn = occurredOn,
        recordedAt = recordedAt,
        appVersion = appVersion,
        previousHash = previousHash,
        hash = hash,
    )

    private companion object {
        const val CHUNK = 500
    }
}
