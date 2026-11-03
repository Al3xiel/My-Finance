package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticPackageWriter
import com.al3xiel.my_finances.feature.diagnostics.domain.model.BuiltPackage
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageAlteredEvent
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageEventLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageManifest

import com.al3xiel.my_finances.core.security.container.BackupPassword
import com.al3xiel.my_finances.core.security.container.ContainerHeader
import com.al3xiel.my_finances.core.security.container.EncryptedContainerWriter
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageFormat.json
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageInput
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ExportedLedgerLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.reports.data.CsvReportWriter
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.LocalDateTime
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * Arma el `.fdiag` (04 §3.3.1, D54): cinco entradas en orden fijo y con fecha fija, de modo que el mismo contenido da los mismos
 * bytes. No lee la base ni dibuja el PDF; recibe todo en [DiagnosticPackageInput]. Nada se escribe a disco.
 */
class DiagnosticPackageBuilder(private val iterations: Int) : DiagnosticPackageWriter {
    @Inject
    constructor() : this(ContainerHeader.DEFAULT_ITERATIONS)

    override fun build(input: DiagnosticPackageInput, password: CharArray?): BuiltPackage = build(input, password, SecureRandom())

    /** Borra `password` al terminar, haya o no contraseña que usar. */
    internal fun build(input: DiagnosticPackageInput, password: CharArray?, random: SecureRandom): BuiltPackage {
        try {
            val contents = LinkedHashMap<String, ByteArray>()
            contents[DiagnosticPackageFormat.SUMMARY] = input.summaryPdf
            contents[DiagnosticPackageFormat.LEDGER] = ledgerCsv(input.projections.ledger).toByteArray(Charsets.UTF_8)
            contents[DiagnosticPackageFormat.EVENTS] = eventsJsonl(input).toByteArray(Charsets.UTF_8)
            contents[DiagnosticPackageFormat.PROJECTIONS] = projectionsJson(input).toByteArray(Charsets.UTF_8)
            val contentHash = DiagnosticPackageFormat.contentHash(contents)
            contents[DiagnosticPackageFormat.MANIFEST] = manifestJson(input, contentHash).toByteArray(Charsets.UTF_8)

            val zip = zip(contents)
            val packageHash = DiagnosticPackageFormat.sha256(zip)
            val file = if (password == null) zip else encrypt(zip, password, random)
            return BuiltPackage(file, zip, packageHash, contentHash)
        } finally {
            if (password != null) BackupPassword.wipe(password)
        }
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.setLevel(Deflater.DEFAULT_COMPRESSION)
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                entry.setTimeLocal(LocalDateTime.of(DiagnosticPackageFormat.ENTRY_YEAR, 1, 1, 0, 0, 0))
                zip.putNextEntry(entry)
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun encrypt(zip: ByteArray, password: CharArray, random: SecureRandom): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptedContainerWriter(out, DiagnosticPackageFormat.MAGIC, password, iterations = iterations, random = random).use { it.write(zip) }
        return out.toByteArray()
    }

    private fun ledgerCsv(ledger: List<ExportedLedgerLine>): String {
        val text = StringBuilder(HEADER).append(EOL)
        for (line in ledger) {
            val cells = listOf(
                line.sequence.toString(),
                CsvReportWriter.cell(line.movementId.orEmpty()),
                CsvReportWriter.cell(line.accountId),
                CsvReportWriter.cell(line.entryKind),
                CsvReportWriter.cell(line.occurredOn),
                CsvReportWriter.amountText(line.deltaMinor),
                CsvReportWriter.amountText(line.balanceMinor),
                line.isVoided.toString(),
                line.voidedSequence?.toString().orEmpty(),
            )
            text.append(cells.joinToString(",")).append(EOL)
        }
        return text.toString()
    }

    private fun eventsJsonl(input: DiagnosticPackageInput): String = input.events.joinToString("") { event ->
        val stored = event.source
        val line = PackageEventLine(
            sequence = stored.sequence,
            eventId = stored.eventId,
            type = stored.event.type,
            schemaVersion = stored.schemaVersion,
            aggregateType = stored.event.aggregateType.name,
            aggregateId = stored.event.aggregateId,
            correlationId = stored.correlationId,
            payload = event.payload,
            occurredOn = stored.occurredOn.toString(),
            recordedAt = stored.recordedAt.toEpochMilli(),
            appVersion = stored.appVersion,
            previousHash = stored.previousHash,
            hash = stored.hash,
        )
        json.encodeToString(PackageEventLine.serializer(), line) + "\n"
    }

    private fun projectionsJson(input: DiagnosticPackageInput): String {
        val projections = input.projections
        val shaped = PackageProjections(
            accountBalances = projections.accountBalances.toSortedMap(),
            ledger = projections.ledger,
            reconciliation = projections.reconciliation,
            projectorVersions = projections.projectorVersions.toSortedMap(),
            originBalances = if (input.scope == DiagnosticScope.FULL) projections.originBalances else null,
        )
        return json.encodeToString(PackageProjections.serializer(), shaped)
    }

    private fun manifestJson(input: DiagnosticPackageInput, contentHash: String): String {
        val manifest = PackageManifest(
            formatVersion = DiagnosticPackageFormat.FORMAT_VERSION,
            exportId = input.exportId,
            appVersion = input.appVersion,
            androidVersion = input.androidVersion,
            exportedAt = input.exportedAt.toString(),
            scope = input.scope.name,
            reconciliationId = input.reconciliationId?.value,
            anonymized = input.anonymized,
            exportedUpToSequence = input.exportedUpToSequence,
            eventCount = input.events.size,
            projectorVersions = input.projections.projectorVersions.toSortedMap(),
            alteredEvents = input.alteredEvents.map { PackageAlteredEvent(it.sequence, it.originalHash, it.fields) },
            contentHash = contentHash,
        )
        return json.encodeToString(PackageManifest.serializer(), manifest)
    }

    private companion object {
        const val EOL = "\r\n"
        const val HEADER = "sequence,movementId,accountId,kind,occurredOn,delta,balance,voided,voidedSequence"
    }
}
