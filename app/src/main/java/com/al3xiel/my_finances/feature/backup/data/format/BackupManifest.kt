package com.al3xiel.my_finances.feature.backup.data.format

import com.al3xiel.my_finances.core.security.container.ContainerLimits
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.HashedFields
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

object BackupFormat {
    /** "FBAK": identifica el contenedor cifrado de un respaldo. */
    val MAGIC = byteArrayOf('F'.code.toByte(), 'B'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte())

    internal val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }
}

/**
 * Primera línea del flujo descifrado (D45). Describe el archivo sin montos ni nombres: es lo único que se muestra
 * antes de confirmar una restauración. `chainIntact` solo informa; restaurar exige que la cadena verifique de verdad.
 */
@Serializable
data class BackupManifest(
    val backupId: String,
    val createdAt: Long,
    val appVersion: String,
    val dbSchemaVersion: Int,
    val maxEventSchemaVersion: Int,
    val eventCount: Long,
    val lastHash: String,
    val chainIntact: Boolean,
    val firstOccurredOn: String?,
    val lastOccurredOn: String?,
)

/** Una fila cruda de `events`, tal como se guarda: nunca se decodifica para respaldarla. */
@Serializable
internal data class BackupRow(
    val sequence: Long,
    val hash: String,
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
) {
    fun toChainRow() = ChainRow(
        sequence, hash,
        HashedFields(
            previousHash, eventId, type, schemaVersion, aggregateType, aggregateId,
            correlationId, payload, occurredOn, recordedAt, appVersion,
        ),
    )

    companion object {
        fun of(row: ChainRow) = with(row.fields) {
            BackupRow(
                row.sequence, row.hash, previousHash, eventId, type, schemaVersion, aggregateType, aggregateId,
                correlationId, payload, occurredOn, recordedAt, appVersion,
            )
        }
    }
}

/** Límites duros para un archivo que no es de fiar (D49). */
data class BackupLimits(
    val maxFileBytes: Long = 256L * 1024 * 1024,
    val maxEvents: Long = 1_000_000,
    val maxLineBytes: Int = 1024 * 1024,
    val maxDecompressedBytes: Long = 1024L * 1024 * 1024,
    val container: ContainerLimits = ContainerLimits(),
)

/** Por qué no se acepta un archivo; nunca lleva el contenido del archivo ni la contraseña. */
class BackupFormatException(val reason: Reason) : IOException(reason.name) {
    enum class Reason {
        NOT_A_BACKUP,
        UNSUPPORTED_VERSION,
        WRONG_PASSWORD_OR_TAMPERED,
        TAMPERED_OR_TRUNCATED,
        TOO_LARGE,
        TOO_MANY_EVENTS,
        LINE_TOO_LONG,
        DECOMPRESSED_TOO_LARGE,
        NEWER_SCHEMA,
        UNKNOWN_EVENT_TYPE,
        INVALID_JSON,
        COUNT_MISMATCH,
        CHAIN_BROKEN,
    }
}
