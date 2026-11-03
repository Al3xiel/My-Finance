package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageException.Reason
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Un `.fdiag` llega de otra persona (DG5): cada defecto se rechaza con su motivo y sin mostrar su contenido. */
class HostilePackageTest {
    private val importer = DiagnosticPackageImporter()

    private fun reject(bytes: ByteArray, password: CharArray? = null, using: DiagnosticPackageImporter = importer): Reason {
        try {
            using.read(ByteArrayInputStream(bytes), bytes.size.toLong(), password)
        } catch (e: DiagnosticPackageException) {
            assertTrue("the message is only the reason", e.message == e.reason.name)
            return e.reason
        }
        fail("expected a rejection")
        error("unreachable")
    }

    private fun entries() = PackageFixtures.entriesOf(PackageFixtures.valid().zip)

    @Test
    fun zipSlipNamesAreRejectedAndNeverUsedAsPaths() {
        val bad = entries().map { it.key to it.value } + ("../evil.txt" to ByteArray(4))
        assertEquals(Reason.TOO_MANY_ENTRIES, reject(PackageFixtures.zipOf(bad)))

        val renamed = entries().map { (if (it.key == "ledger.csv") "../ledger.csv" else it.key) to it.value }
        assertEquals(Reason.UNEXPECTED_ENTRY, reject(PackageFixtures.zipOf(renamed)))
    }

    @Test
    fun anEntryThatIsTooBigIsRejectedWhileReading() {
        val small = DiagnosticPackageImporter(PackageLimits(maxEntryBytes = 1_000))
        val big = PackageFixtures.withReplaced("ledger.csv", ByteArray(5_000) { 'a'.code.toByte() })
        assertEquals(Reason.ENTRY_TOO_LARGE, reject(big, using = small))
    }

    @Test
    fun aDecompressionBombIsStoppedLongBeforeItsFullSize() {
        val limited = DiagnosticPackageImporter(PackageLimits(maxEntryBytes = 1_000_000))
        val bomb = PackageFixtures.withReplaced("summary.pdf", ByteArray(50_000_000))
        assertTrue("the bomb itself is tiny", bomb.size < 200_000)
        assertEquals(Reason.ENTRY_TOO_LARGE, reject(bomb, using = limited))
    }

    @Test
    fun theTotalDecompressedSizeIsAlsoCapped() {
        val limited = DiagnosticPackageImporter(PackageLimits(maxEntryBytes = 1_000_000, maxTotalBytes = 1_500_000))
        val entries = entries()
        entries["summary.pdf"] = ByteArray(900_000)
        entries["ledger.csv"] = ByteArray(900_000)
        assertEquals(Reason.ENTRY_TOO_LARGE, reject(PackageFixtures.zipOf(entries.map { it.key to it.value }), using = limited))
    }

    @Test
    fun aFileLargerThanTheLimitIsRejectedBeforeReading() {
        val limited = DiagnosticPackageImporter(PackageLimits(maxFileBytes = 100))
        assertEquals(Reason.TOO_LARGE, reject(PackageFixtures.valid().file, using = limited))
    }

    @Test
    fun extraEntriesAreRejected() {
        val extra = entries().map { it.key to it.value } + ("extra.txt" to ByteArray(1))
        assertEquals(Reason.TOO_MANY_ENTRIES, reject(PackageFixtures.zipOf(extra)))
    }

    @Test
    fun aMissingEntryIsRejected() {
        val missing = entries().filterKeys { it != "ledger.csv" }.map { it.key to it.value }
        assertEquals(Reason.MISSING_ENTRY, reject(PackageFixtures.zipOf(missing)))
    }

    @Test
    fun aRepeatedEntryIsRejected() {
        // Cinco entradas, una con el nombre de otra: "summarx.pdf" pasa a "summary.pdf" con el mismo largo.
        val withTwin = PackageFixtures.zipOf(entries().map { it.key to it.value }.toMutableList().also { it[1] = "summarx.pdf" to it[1].second })
        val bytes = String(withTwin, Charsets.ISO_8859_1).replace("summarx.pdf", "summary.pdf").toByteArray(Charsets.ISO_8859_1)
        assertEquals(Reason.DUPLICATE_ENTRY, reject(bytes))
    }

    @Test
    fun malformedJsonInTheManifestIsRejected() {
        assertEquals(Reason.MALFORMED, reject(PackageFixtures.withReplaced("manifest.json", "{ not json".toByteArray())))
    }

    @Test
    fun malformedProjectionsAreRejectedAfterTheHashCheck() {
        assertEquals(Reason.HASH_MISMATCH, reject(PackageFixtures.withReplaced("projections.json", "{}".toByteArray())))
    }

    @Test
    fun anAlteredEntryChangesTheContentHash() {
        val ledger = entries().getValue("ledger.csv")
        ledger[ledger.size - 4] = (ledger[ledger.size - 4].toInt() xor 1).toByte()
        assertEquals(Reason.HASH_MISMATCH, reject(PackageFixtures.withReplaced("ledger.csv", ledger)))
    }

    @Test
    fun aPackageFromANewerFormatIsRejectedWithItsOwnReason() {
        val manifest = String(entries().getValue("manifest.json"), Charsets.UTF_8).replace("\"formatVersion\":1", "\"formatVersion\":99")
        assertEquals(Reason.NEWER_VERSION, reject(PackageFixtures.withReplaced("manifest.json", manifest.toByteArray())))
    }

    @Test
    fun anUnknownEventTypeIsRejectedWithTheSameReasonAsANewerSchema() {
        val events = String(entries().getValue("events.jsonl"), Charsets.UTF_8)
        val renamed = events.replace("\"type\":\"AccountCreated\"", "\"type\":\"AccountTeleported\"")
        assertEquals(Reason.UNKNOWN_EVENT, reject(rebuild("events.jsonl", renamed.toByteArray())))
    }

    @Test
    fun aNewerEventSchemaIsRejected() {
        val events = String(entries().getValue("events.jsonl"), Charsets.UTF_8)
        val newer = events.replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":9")
        assertEquals(Reason.UNKNOWN_EVENT, reject(rebuild("events.jsonl", newer.toByteArray())))
    }

    @Test
    fun aLineThatIsTooLongIsRejected() {
        val limited = DiagnosticPackageImporter(PackageLimits(maxLineBytes = 100))
        assertEquals(Reason.LINE_TOO_LONG, reject(PackageFixtures.valid().file, using = limited))
    }

    @Test
    fun tooManyEventsAreRejected() {
        val limited = DiagnosticPackageImporter(PackageLimits(maxEvents = 2))
        assertEquals(Reason.TOO_MANY_EVENTS, reject(PackageFixtures.valid().file, using = limited))
    }

    @Test
    fun aWrongPasswordIsReportedAsSuch() {
        val encrypted = PackageFixtures.valid(PackageFixtures.password)
        assertEquals(Reason.WRONG_PASSWORD, reject(encrypted.file, "wrong password!!".toCharArray()))
    }

    @Test
    fun anEncryptedPackageWithoutPasswordAsksForOne() {
        assertEquals(Reason.PASSWORD_REQUIRED, reject(PackageFixtures.valid(PackageFixtures.password).file, null))
    }

    @Test
    fun aTamperedEncryptedPackageIsRejected() {
        val file = PackageFixtures.valid(PackageFixtures.password).file.copyOf()
        file[file.size - 5] = (file[file.size - 5].toInt() xor 1).toByte()
        assertEquals(Reason.WRONG_PASSWORD, reject(file, PackageFixtures.password.copyOf()))
    }

    @Test
    fun randomBytesAreNotAPackage() {
        assertEquals(Reason.NOT_A_PACKAGE, reject(ByteArray(200) { it.toByte() }))
        assertEquals(Reason.NOT_A_PACKAGE, reject(ByteArray(0)))
    }

    private fun rebuild(name: String, bytes: ByteArray): ByteArray {
        // El manifiesto lleva el hash del contenido: se recalcula para que el defecto probado sea el del evento.
        val entries = entries()
        entries[name] = bytes
        val contents = DiagnosticPackageFormat.ENTRIES.filter { it != DiagnosticPackageFormat.MANIFEST }.associateWith { entries.getValue(it) }
        val manifest = String(entries.getValue("manifest.json"), Charsets.UTF_8)
            .replace(Regex("\"contentHash\":\"[0-9a-f]+\""), "\"contentHash\":\"${DiagnosticPackageFormat.contentHash(contents)}\"")
        entries["manifest.json"] = manifest.toByteArray()
        return PackageFixtures.zipOf(entries.map { it.key to it.value })
    }
}
