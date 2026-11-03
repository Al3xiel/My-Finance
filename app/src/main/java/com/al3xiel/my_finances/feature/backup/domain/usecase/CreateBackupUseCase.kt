package com.al3xiel.my_finances.feature.backup.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.security.container.ContainerHeader
import com.al3xiel.my_finances.domain.model.event.BackupCreated
import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.usecase.ChainVerifier
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.backup.domain.format.BackupLimits
import com.al3xiel.my_finances.feature.backup.domain.format.BackupManifest
import com.al3xiel.my_finances.feature.backup.domain.format.BackupReader
import com.al3xiel.my_finances.feature.backup.domain.format.BackupWriter
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import java.io.IOException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Named

sealed interface CreateBackupResult {
    data class Created(val eventCount: Long, val chainIntact: Boolean) : CreateBackupResult

    /** El usuario cerró el selector sin elegir dónde guardar. */
    data object Cancelled : CreateBackupResult

    data class WriteFailed(val documentDeleted: Boolean) : CreateBackupResult

    data class VerificationFailed(val documentDeleted: Boolean) : CreateBackupResult
}

/**
 * Respalda el registro en el documento elegido: recorre la cadena, escribe, reabre el archivo y comprueba todas las
 * etiquetas de bloque; solo entonces emite `BackupCreated` (D46). Si algo falla intenta borrar el documento a medias.
 *
 * Una cadena rota no impide respaldar: queda `chainIntact = false` (P6). La re-autenticación del bloqueo (D48) la pide
 * el ViewModel antes de llamar; el caso de uso hace trabajo pesado y debe llamarse fuera del hilo principal.
 */
class CreateBackupUseCase internal constructor(
    private val eventStore: EventStore,
    private val files: BackupFilePort,
    private val recorder: EventRecorder,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val appVersion: String,
    private val dbSchemaVersion: Int,
    private val iterations: Int,
) {
    @Inject
    constructor(
        eventStore: EventStore,
        files: BackupFilePort,
        recorder: EventRecorder,
        clock: Clock,
        ids: IdGenerator,
        @Named("appVersion") appVersion: String,
        @Named("dbSchemaVersion") dbSchemaVersion: Int,
    ) : this(eventStore, files, recorder, clock, ids, appVersion, dbSchemaVersion, ContainerHeader.DEFAULT_ITERATIONS)

    /** `uri` nulo = el usuario canceló el selector. La contraseña la borra quien llama. */
    suspend operator fun invoke(uri: String?, password: CharArray): CreateBackupResult {
        if (uri == null) return CreateBackupResult.Cancelled
        val manifest = summarize()
        try {
            val output = files.openOutput(uri) ?: return CreateBackupResult.WriteFailed(documentDeleted = true)
            BackupWriter(output, password, manifest, iterations).use { writer -> copyRows(writer, manifest.eventCount) }
        } catch (_: IOException) {
            return CreateBackupResult.WriteFailed(files.delete(uri))
        }
        if (!verifyWritten(uri, password, manifest)) return CreateBackupResult.VerificationFailed(files.delete(uri))
        recorder.record(BackupCreated(manifest.backupId, manifest.eventCount, manifest.lastHash))
        return CreateBackupResult.Created(manifest.eventCount, manifest.chainIntact)
    }

    /** Primera pasada: cuenta, rango de fechas y estado de la cadena; las filas no se guardan. */
    private suspend fun summarize(): BackupManifest {
        val verifier = ChainVerifier()
        var broken = false
        var count = 0L
        var lastHash = EventHasher.GENESIS
        var maxSchema = 1
        var first: String? = null
        var last: String? = null
        var after = 0L
        while (true) {
            val page = eventStore.readChainPage(after, PAGE_SIZE)
            if (page.isEmpty()) break
            for (row in page) {
                if (!broken && verifier.accept(row) != null) broken = true
                count++
                lastHash = row.hash
                maxSchema = maxOf(maxSchema, row.fields.schemaVersion)
                val day = row.fields.occurredOn
                if (first == null || day < first) first = day
                if (last == null || day > last) last = day
            }
            after = page.last().sequence
        }
        return BackupManifest(
            backupId = ids.newId(),
            createdAt = clock.millis(),
            appVersion = appVersion,
            dbSchemaVersion = dbSchemaVersion,
            maxEventSchemaVersion = maxSchema,
            eventCount = count,
            lastHash = lastHash,
            chainIntact = !broken,
            firstOccurredOn = first,
            lastOccurredOn = last,
        )
    }

    /** Segunda pasada: exactamente las filas contadas, aunque entren eventos nuevos entretanto. */
    private suspend fun copyRows(writer: BackupWriter, total: Long) {
        var written = 0L
        var after = 0L
        while (written < total) {
            val page = eventStore.readChainPage(after, PAGE_SIZE).take((total - written).toInt().coerceAtMost(PAGE_SIZE))
            if (page.isEmpty()) throw IOException("registry changed while writing")
            writer.append(page)
            written += page.size
            after = page.last().sequence
        }
    }

    private suspend fun verifyWritten(uri: String, password: CharArray, manifest: BackupManifest): Boolean = try {
        val input = files.openInput(uri)
        if (input == null) {
            false
        } else {
            BackupReader.open(input, password, files.sizeOf(uri) ?: 0L, BackupLimits()).use { reader ->
                reader.manifest == manifest && reader.verifyAll(requireIntactChain = false) == manifest.eventCount
            }
        }
    } catch (_: IOException) {
        false
    }

    private companion object {
        const val PAGE_SIZE = 500
    }
}
