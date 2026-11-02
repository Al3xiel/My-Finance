package com.al3xiel.my_finances.feature.backup.data

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.domain.model.event.BackupRestored
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.HashedFields
import com.al3xiel.my_finances.domain.usecase.ChainVerifier
import com.al3xiel.my_finances.feature.backup.data.format.BackupFormatException
import com.al3xiel.my_finances.feature.backup.data.format.BackupFormatException.Reason
import com.al3xiel.my_finances.feature.backup.data.format.BackupReader
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Named

/** Base de staging nueva donde se importa; la base real no participa. */
interface StagingSink {
    /** Inserta las filas con su `sequence` original. */
    suspend fun insert(rows: List<ChainRow>)

    /** Cierra el staging con todo escrito, listo para intercambiarse. */
    suspend fun commit()

    /** Cierra y borra el staging con sus archivos auxiliares. */
    suspend fun discard()
}

fun interface StagingSinkFactory {
    suspend fun create(): StagingSink
}

data class ImportedBackup(val eventCount: Long, val lastHash: String)

/**
 * Importa un respaldo al staging verificando la cadena fila a fila y decodificando cada evento, y agrega
 * `BackupRestored` encadenado al último hash del archivo (D46). Cualquier fallo borra el staging (D49).
 */
class BackupImporter @Inject constructor(
    private val sinks: StagingSinkFactory,
    private val ids: IdGenerator,
    private val clock: Clock,
    @Named("appVersion") private val appVersion: String,
) {
    suspend fun import(reader: BackupReader): ImportedBackup {
        val sink = sinks.create()
        try {
            val verifier = ChainVerifier()
            while (true) {
                val batch = reader.nextBatch(BATCH)
                if (batch.isEmpty()) break
                for (row in batch) {
                    if (verifier.accept(row) != null) throw BackupFormatException(Reason.CHAIN_BROKEN)
                    reader.checkEvent(row)
                }
                sink.insert(batch)
            }
            val manifest = reader.manifest
            if (verifier.lastHash != manifest.lastHash) throw BackupFormatException(Reason.CHAIN_BROKEN)
            val restored = restoredRow(manifest.backupId, verifier.count.toLong(), verifier.lastHash)
            sink.insert(listOf(restored))
            sink.commit()
            return ImportedBackup(verifier.count.toLong(), restored.hash)
        } catch (e: Throwable) {
            withContext(NonCancellable) { runCatching { sink.discard() } }
            throw e
        }
    }

    private fun restoredRow(backupId: String, count: Long, previousHash: String): ChainRow {
        val event = BackupRestored(backupId, count)
        val fields = HashedFields(
            previousHash = previousHash,
            eventId = ids.newId(),
            type = event.type,
            schemaVersion = event.schemaVersion,
            aggregateType = event.aggregateType.name,
            aggregateId = event.aggregateId,
            correlationId = ids.newId(),
            payload = EventCodec.encode(event),
            occurredOn = LocalDate.now(clock).toString(),
            recordedAt = clock.millis(),
            appVersion = appVersion,
        )
        return ChainRow(count + 1, EventHasher.hash(fields), fields)
    }

    private companion object {
        const val BATCH = 500
    }
}
