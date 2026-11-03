package com.al3xiel.my_finances.feature.backup.domain.format

import com.al3xiel.my_finances.core.security.container.ContainerException
import com.al3xiel.my_finances.core.security.container.EncryptedContainerReader
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.usecase.ChainVerifier
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.EOFException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException
import com.al3xiel.my_finances.feature.backup.domain.format.BackupFormatException.Reason

/**
 * Lee un `.fbak` que no es de fiar. [open] valida tamaño, cabecera, contraseña y manifiesto sin leer las filas
 * (eso es el resumen); [nextBatch] entrega filas por lotes y [verifyAll] recorre todo el archivo.
 * Toda falla sale como [BackupFormatException]; el contenido del archivo nunca va en el mensaje.
 */
class BackupReader private constructor(
    private val stream: CountingStream,
    private val limits: BackupLimits,
    val manifest: BackupManifest,
) : Closeable {
    private var rowsRead = 0L

    /** Hasta [max] filas; lista vacía al terminar (y entonces comprueba que la cantidad coincide con el manifiesto). */
    fun nextBatch(max: Int): List<ChainRow> {
        val batch = ArrayList<ChainRow>(max)
        while (batch.size < max) {
            val line = readLine(stream, limits.maxLineBytes) ?: break
            if (rowsRead + 1 > limits.maxEvents) throw BackupFormatException(Reason.TOO_MANY_EVENTS)
            if (rowsRead + 1 > manifest.eventCount) throw BackupFormatException(Reason.COUNT_MISMATCH)
            rowsRead++
            batch += parse<BackupRow>(line).toChainRow()
        }
        if (batch.isEmpty() && rowsRead != manifest.eventCount) throw BackupFormatException(Reason.COUNT_MISMATCH)
        return batch
    }

    /**
     * Recorre lo que falta del archivo y devuelve cuántas filas tiene. Con [requireIntactChain] (restaurar) comprueba
     * además la cadena, el último hash, que cada tipo se conozca y que cada payload se decodifique; sin él (comprobar
     * un respaldo recién escrito) solo exige un archivo íntegro y bien formado.
     */
    fun verifyAll(requireIntactChain: Boolean): Long {
        val verifier = ChainVerifier()
        while (true) {
            val batch = nextBatch(BATCH)
            if (batch.isEmpty()) break
            if (!requireIntactChain) continue
            for (row in batch) {
                if (verifier.accept(row) != null) throw BackupFormatException(Reason.CHAIN_BROKEN)
                checkEvent(row)
            }
        }
        if (requireIntactChain && verifier.lastHash != manifest.lastHash) throw BackupFormatException(Reason.CHAIN_BROKEN)
        return rowsRead
    }

    override fun close() = stream.close()

    fun checkEvent(row: ChainRow) {
        val fields = row.fields
        if (fields.type !in EventCodec.types) throw BackupFormatException(Reason.UNKNOWN_EVENT_TYPE)
        if (fields.schemaVersion > DomainEvent.CURRENT_SCHEMA_VERSION) throw BackupFormatException(Reason.NEWER_SCHEMA)
        try {
            EventCodec.decode(fields.type, fields.schemaVersion, fields.payload)
        } catch (_: Exception) {
            throw BackupFormatException(Reason.INVALID_JSON)
        }
    }

    companion object {
        private const val BATCH = 500

        fun open(input: InputStream, password: CharArray, sizeBytes: Long, limits: BackupLimits = BackupLimits()): BackupReader {
            if (sizeBytes > limits.maxFileBytes) throw BackupFormatException(Reason.TOO_LARGE)
            var counting: CountingStream? = null
            try {
                val container = EncryptedContainerReader(input, BackupFormat.MAGIC, password, limits.container)
                counting = CountingStream(BufferedInputStream(GZIPInputStream(container)), limits.maxDecompressedBytes)
                val first = readLine(counting, limits.maxLineBytes) ?: throw BackupFormatException(Reason.NOT_A_BACKUP)
                val manifest = parse<BackupManifest>(first)
                if (manifest.maxEventSchemaVersion > DomainEvent.CURRENT_SCHEMA_VERSION) throw BackupFormatException(Reason.NEWER_SCHEMA)
                if (manifest.eventCount < 0 || manifest.eventCount > limits.maxEvents) throw BackupFormatException(Reason.TOO_MANY_EVENTS)
                return BackupReader(counting, limits, manifest)
            } catch (e: Throwable) {
                runCatching { counting?.close() ?: input.close() }
                throw when (e) {
                    is ContainerException -> BackupFormatException(e.reason.toFormatReason())
                    is ZipException, is EOFException -> BackupFormatException(Reason.TAMPERED_OR_TRUNCATED)
                    else -> e
                }
            }
        }

        private inline fun <reified T> parse(line: String): T = try {
            BackupFormat.json.decodeFromString<T>(line)
        } catch (_: SerializationException) {
            throw BackupFormatException(Reason.INVALID_JSON)
        } catch (_: IllegalArgumentException) {
            throw BackupFormatException(Reason.INVALID_JSON)
        }

        /** Una línea con largo máximo; `null` al terminar el flujo. */
        private fun readLine(stream: CountingStream, maxBytes: Int): String? {
            val line = ByteArrayOutputStream()
            try {
                while (true) {
                    val next = stream.read()
                    if (next < 0) return if (line.size() == 0) null else throw BackupFormatException(Reason.TAMPERED_OR_TRUNCATED)
                    if (next == '\n'.code) return line.toString(Charsets.UTF_8.name())
                    if (line.size() >= maxBytes) throw BackupFormatException(Reason.LINE_TOO_LONG)
                    line.write(next)
                }
            } catch (e: ContainerException) {
                throw BackupFormatException(e.reason.toFormatReason())
            } catch (_: ZipException) {
                throw BackupFormatException(Reason.TAMPERED_OR_TRUNCATED)
            } catch (_: EOFException) {
                throw BackupFormatException(Reason.TAMPERED_OR_TRUNCATED)
            }
        }

        private fun ContainerException.Reason.toFormatReason() = when (this) {
            ContainerException.Reason.NOT_A_CONTAINER -> Reason.NOT_A_BACKUP
            ContainerException.Reason.UNSUPPORTED_VERSION -> Reason.UNSUPPORTED_VERSION
            ContainerException.Reason.LIMITS_EXCEEDED -> Reason.TOO_LARGE
            ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED -> Reason.WRONG_PASSWORD_OR_TAMPERED
            ContainerException.Reason.TAMPERED_OR_TRUNCATED -> Reason.TAMPERED_OR_TRUNCATED
        }
    }
}

/** Cuenta los bytes ya descomprimidos y corta al pasar el límite: una bomba de compresión no llega a la memoria. */
private class CountingStream(private val source: InputStream, private val max: Long) : InputStream() {
    private var total = 0L

    override fun read(): Int {
        val next = source.read()
        if (next >= 0 && ++total > max) throw BackupFormatException(Reason.DECOMPRESSED_TOO_LARGE)
        return next
    }

    override fun close() = source.close()
}
