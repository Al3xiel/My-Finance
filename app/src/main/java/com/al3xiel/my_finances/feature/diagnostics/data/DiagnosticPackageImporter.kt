package com.al3xiel.my_finances.feature.diagnostics.data
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticPackageReader
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageException
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageEventLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageManifest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ParsedPackage

import com.al3xiel.my_finances.core.security.container.ContainerException
import com.al3xiel.my_finances.core.security.container.ContainerLimits
import com.al3xiel.my_finances.core.security.container.EncryptedContainerReader
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.DomainEvent
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageException.Reason
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

/** Topes sobre un archivo que no es de confianza (DG5, P13): se validan antes y durante la lectura. */
data class PackageLimits(
    val maxFileBytes: Long = 64L * 1024 * 1024,
    val maxEntryBytes: Long = 64L * 1024 * 1024,
    val maxTotalBytes: Long = 160L * 1024 * 1024,
    val maxEvents: Long = 200_000,
    val maxLineBytes: Int = 1024 * 1024,
    val container: ContainerLimits = ContainerLimits(),
)

/**
 * Lee un `.fdiag` que llegó de otra persona: todo en memoria, con topes duros, sin extraer a disco y sin usar nunca el nombre
 * de una entrada como ruta. Rechaza con un [Reason] y nada más; no verifica la cadena de hashes (D54: viene anonimizada).
 */
class DiagnosticPackageImporter(private val limits: PackageLimits) : DiagnosticPackageReader {
    @Inject
    constructor() : this(PackageLimits())

    /** `password` lo borra quien llama. */
    override fun read(input: InputStream, sizeBytes: Long, password: CharArray?): ParsedPackage {
        if (sizeBytes > limits.maxFileBytes) throw DiagnosticPackageException(Reason.TOO_LARGE)
        val file = readBounded(input, limits.maxFileBytes, Reason.TOO_LARGE)
        val zip = if (file.startsWithBytes(DiagnosticPackageFormat.MAGIC)) decrypt(file, password) else file
        if (!zip.startsWithBytes(ZIP_MAGIC)) throw DiagnosticPackageException(Reason.NOT_A_PACKAGE)
        val entries = try {
            entriesOf(zip)
        } catch (e: DiagnosticPackageException) {
            throw e
        } catch (_: IOException) {
            throw DiagnosticPackageException(Reason.MALFORMED)
        }
        return parse(entries, DiagnosticPackageFormat.sha256(zip))
    }

    private fun decrypt(file: ByteArray, password: CharArray?): ByteArray {
        if (password == null) throw DiagnosticPackageException(Reason.PASSWORD_REQUIRED)
        try {
            return EncryptedContainerReader(ByteArrayInputStream(file), DiagnosticPackageFormat.MAGIC, password, limits.container)
                .use { readBounded(it, limits.maxFileBytes, Reason.TOO_LARGE) }
        } catch (e: ContainerException) {
            throw DiagnosticPackageException(
                when (e.reason) {
                    ContainerException.Reason.NOT_A_CONTAINER -> Reason.NOT_A_PACKAGE
                    ContainerException.Reason.UNSUPPORTED_VERSION -> Reason.NEWER_VERSION
                    ContainerException.Reason.LIMITS_EXCEEDED -> Reason.TOO_LARGE
                    ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED, ContainerException.Reason.TAMPERED_OR_TRUNCATED -> Reason.WRONG_PASSWORD
                },
            )
        }
    }

    /** Las cinco entradas exactas, una vez cada una; el nombre solo se compara con la lista fija. */
    private fun entriesOf(zip: ByteArray): Map<String, ByteArray> {
        val found = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(zip)).use { stream ->
            var count = 0
            while (true) {
                val entry = stream.nextEntry ?: break
                if (++count > DiagnosticPackageFormat.ENTRIES.size) throw DiagnosticPackageException(Reason.TOO_MANY_ENTRIES)
                val name = entry.name
                if (name !in DiagnosticPackageFormat.ENTRIES) throw DiagnosticPackageException(Reason.UNEXPECTED_ENTRY)
                if (name in found) throw DiagnosticPackageException(Reason.DUPLICATE_ENTRY)
                val bytes = readBounded(stream, minOf(limits.maxEntryBytes, limits.maxTotalBytes - total), Reason.ENTRY_TOO_LARGE)
                total += bytes.size
                found[name] = bytes
            }
        }
        if (DiagnosticPackageFormat.ENTRIES.any { it !in found }) throw DiagnosticPackageException(Reason.MISSING_ENTRY)
        return found
    }

    private fun parse(entries: Map<String, ByteArray>, packageHash: String): ParsedPackage {
        val manifest = decode(PackageManifest.serializer(), entries.getValue(DiagnosticPackageFormat.MANIFEST))
        if (manifest.formatVersion > DiagnosticPackageFormat.FORMAT_VERSION) throw DiagnosticPackageException(Reason.NEWER_VERSION)
        if (manifest.formatVersion < 1) throw DiagnosticPackageException(Reason.MALFORMED)
        if (manifest.eventCount < 0 || manifest.eventCount > limits.maxEvents) throw DiagnosticPackageException(Reason.TOO_MANY_EVENTS)

        val covered = DiagnosticPackageFormat.ENTRIES.filter { it != DiagnosticPackageFormat.MANIFEST }.associateWith { entries.getValue(it) }
        if (DiagnosticPackageFormat.contentHash(covered) != manifest.contentHash) throw DiagnosticPackageException(Reason.HASH_MISMATCH)

        val projections = decode(PackageProjections.serializer(), entries.getValue(DiagnosticPackageFormat.PROJECTIONS))
        val events = events(entries.getValue(DiagnosticPackageFormat.EVENTS))
        if (events.size != manifest.eventCount) throw DiagnosticPackageException(Reason.MALFORMED)
        return ParsedPackage(manifest, projections, events, packageHash)
    }

    private fun events(bytes: ByteArray): List<PackageEventLine> {
        val lines = ArrayList<PackageEventLine>()
        var start = 0
        var previous = 0L
        while (start < bytes.size) {
            var end = start
            while (end < bytes.size && bytes[end] != NEWLINE) end++
            if (end - start > limits.maxLineBytes) throw DiagnosticPackageException(Reason.LINE_TOO_LONG)
            if (lines.size >= limits.maxEvents) throw DiagnosticPackageException(Reason.TOO_MANY_EVENTS)
            val line = decode(PackageEventLine.serializer(), bytes.copyOfRange(start, end))
            validate(line, previous)
            previous = line.sequence
            lines += line
            start = end + 1
        }
        return lines
    }

    private fun validate(line: PackageEventLine, previousSequence: Long) {
        if (line.sequence <= previousSequence) throw DiagnosticPackageException(Reason.MALFORMED)
        if (line.schemaVersion < 1 || line.schemaVersion > DomainEvent.CURRENT_SCHEMA_VERSION) throw DiagnosticPackageException(Reason.UNKNOWN_EVENT)
        if (AggregateType.entries.none { it.name == line.aggregateType }) throw DiagnosticPackageException(Reason.MALFORMED)
        try {
            EventCodec.descriptor(line.type)
        } catch (_: IllegalStateException) {
            throw DiagnosticPackageException(Reason.UNKNOWN_EVENT)
        }
        try {
            EventCodec.decode(line.type, line.schemaVersion, line.payload)
        } catch (_: Exception) {
            throw DiagnosticPackageException(Reason.MALFORMED)
        }
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, bytes: ByteArray): T = try {
        DiagnosticPackageFormat.json.decodeFromString(serializer, String(bytes, Charsets.UTF_8))
    } catch (_: Exception) {
        throw DiagnosticPackageException(Reason.MALFORMED)
    }

    /** Lee a lo sumo `limit` bytes; si hay más, rechaza sin seguir leyendo (bomba de descompresión). */
    private fun readBounded(input: InputStream, limit: Long, reason: Reason): ByteArray {
        if (limit < 0) throw DiagnosticPackageException(reason)
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw DiagnosticPackageException(reason)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun ByteArray.startsWithBytes(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private companion object {
        const val BUFFER = 16 * 1024
        const val NEWLINE = '\n'.code.toByte()
        val ZIP_MAGIC = byteArrayOf('P'.code.toByte(), 'K'.code.toByte(), 3, 4)
    }
}
