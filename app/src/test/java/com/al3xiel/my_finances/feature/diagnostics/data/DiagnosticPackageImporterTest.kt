package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.feature.diagnostics.domain.model.BuiltPackage

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageInput
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ExportedLedgerLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

/** Un paquete válido y utilidades para estropearlo, compartidas por las pruebas del importador. */
internal object PackageFixtures {
    val password = "correct horse battery".toCharArray()
    private val pen = CurrencyCode.PEN

    fun input(): DiagnosticPackageInput {
        val store = FakeEventStore()
        runBlocking {
            store.append(
                "c",
                listOf(
                    PersonCreated(PersonId("p1"), "Ana Pérez"),
                    AccountCreated(AccountId("a1"), "Cuenta Secreta", AccountType.BANK, pen, null, Money(0, pen)),
                    IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(1_000, pen), IncomeSourceId("s1"), null, "nota"),
                ).map { NewEvent(it, LocalDate.of(2026, 10, 25)) },
            )
        }
        val anonymized = EventAnonymizer().anonymize(store.events)
        return DiagnosticPackageInput(
            exportId = "x1",
            scope = DiagnosticScope.FULL,
            reconciliationId = null,
            anonymized = true,
            exportedUpToSequence = store.events.last().sequence,
            exportedAt = Instant.parse("2026-11-02T12:00:00Z"),
            appVersion = "1.0",
            androidVersion = "14",
            events = anonymized.events,
            alteredEvents = anonymized.altered,
            projections = PackageProjections(
                mapOf("a1" to 1_000L),
                listOf(ExportedLedgerLine(3, "m1", "a1", 1_000, "INCOME", "2026-10-25", false, null, 1_000)),
                null,
                mapOf("ledger" to 1),
                null,
            ),
            summaryPdf = ByteArray(16) { it.toByte() },
        )
    }

    fun valid(password: CharArray? = null): BuiltPackage =
        DiagnosticPackageBuilder(1_000).build(input(), password?.copyOf())

    fun entriesOf(zip: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                out[entry.name] = input.readBytes()
            }
        }
        return out
    }

    fun zipOf(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    fun withReplaced(name: String, bytes: ByteArray): ByteArray {
        val entries = entriesOf(valid().zip)
        entries[name] = bytes
        return zipOf(entries.map { it.key to it.value })
    }
}

class DiagnosticPackageImporterTest {
    private val importer = DiagnosticPackageImporter()

    private fun read(bytes: ByteArray, password: CharArray? = null) =
        importer.read(ByteArrayInputStream(bytes), bytes.size.toLong(), password)

    @org.junit.Test
    fun readsAValidPackageAndReturnsTheHashOfThePlainZip() {
        val built = PackageFixtures.valid()
        val parsed = read(built.file)

        org.junit.Assert.assertEquals(built.packageHash, parsed.packageHash)
        org.junit.Assert.assertEquals("x1", parsed.manifest.exportId)
        org.junit.Assert.assertEquals(3, parsed.events.size)
        org.junit.Assert.assertEquals(listOf(1L, 2L, 3L), parsed.events.map { it.sequence })
        org.junit.Assert.assertEquals(1_000L, parsed.projections.accountBalances.getValue("a1"))
    }

    @org.junit.Test
    fun readsAnEncryptedPackageWithItsPasswordAndKeepsThePlainZipHash() {
        val built = PackageFixtures.valid(PackageFixtures.password)
        val parsed = read(built.file, PackageFixtures.password.copyOf())
        org.junit.Assert.assertEquals(built.packageHash, parsed.packageHash)
        org.junit.Assert.assertEquals(3, parsed.events.size)
    }

    @org.junit.Test
    fun theEventsKeepTheirOriginalHashesSoNothingIsRehashed() {
        val parsed = read(PackageFixtures.valid().file)
        val input = PackageFixtures.input()
        org.junit.Assert.assertEquals(input.events.map { it.source.hash }, parsed.events.map { it.hash })
        org.junit.Assert.assertEquals(input.events.map { it.payload }, parsed.events.map { it.payload })
    }
}
