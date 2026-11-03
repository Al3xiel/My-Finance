package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.security.container.ContainerException
import com.al3xiel.my_finances.core.security.container.EncryptedContainerReader
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageInput
import com.al3xiel.my_finances.feature.diagnostics.domain.model.LedgerLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.OriginBalanceLine
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.model.ReconciliationState
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DiagnosticPackageBuilderTest {
    private val pen = CurrencyCode.PEN
    private val builder = DiagnosticPackageBuilder(iterations = 1_000)
    private val password = "correct horse battery".toCharArray()

    private val store = FakeEventStore().also {
        runBlocking {
            it.append(
                "c",
                listOf(
                    NewEvent(AccountCreated(AccountId("a1"), "BBVA Sueldo", AccountType.BANK, pen, "#112233", Money(0, pen)), LocalDate.of(2026, 10, 1)),
                    NewEvent(PersonCreated(PersonId("p1"), "Ana Gómez"), LocalDate.of(2026, 10, 1)),
                    NewEvent(IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(5_000, pen), IncomeSourceId("s1"), null, "sueldo de Ana"), LocalDate.of(2026, 10, 2)),
                ),
            )
        }
    }
    private val anonymized = EventAnonymizer().anonymize(store.events)

    private val projections = PackageProjections(
        accountBalances = mapOf("a1" to 5_000L),
        ledger = listOf(LedgerLine(3, "m1", "a1", 5_000, "INCOME", "2026-10-02", false, null, 5_000)),
        reconciliation = ReconciliationState("r1", "a1", pen, 9_999, 9_000, 3, "OPEN", null, null, false),
        projectorVersions = mapOf("movement" to 1, "catalog" to 1),
        originBalances = listOf(OriginBalanceLine("a1", "s1", 5_000)),
    )

    private fun input(
        scope: DiagnosticScope = DiagnosticScope.RECONCILIATION,
        ledger: List<LedgerLine> = projections.ledger,
        pdf: ByteArray = "%PDF-fixed".toByteArray(),
        events: List<com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.AnonymizedEvent> = anonymized.events,
    ) = DiagnosticPackageInput(
        exportId = "x1",
        scope = scope,
        reconciliationId = ReconciliationId("r1"),
        anonymized = true,
        exportedUpToSequence = 3,
        exportedAt = Instant.parse("2026-11-02T10:00:00Z"),
        appVersion = "1.0.0",
        androidVersion = "16",
        events = events,
        alteredEvents = anonymized.altered,
        projections = projections.copy(ledger = ledger),
        summaryPdf = pdf,
    )

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                assertEquals(1980, entry.timeLocal.year)
                assertEquals(1, entry.timeLocal.monthValue)
                assertEquals(1, entry.timeLocal.dayOfMonth)
                entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun text(entries: Map<String, ByteArray>, name: String) = entries.getValue(name).toString(Charsets.UTF_8)

    @Test
    fun theZipHasExactlyTheFiveEntriesInFixedOrder() {
        val entries = unzip(builder.build(input(), null).zip)

        assertEquals(listOf("summary.pdf", "ledger.csv", "events.jsonl", "projections.json", "manifest.json"), entries.keys.toList())
    }

    @Test
    fun sameContentGivesTheSameBytesAndTheSameHash() {
        val first = builder.build(input(), null)
        val second = DiagnosticPackageBuilder(iterations = 1_000).build(input(), null)

        assertTrue(first.zip.contentEquals(second.zip))
        assertEquals(first.packageHash, second.packageHash)
        assertEquals(first.contentHash, second.contentHash)
    }

    @Test
    fun aDifferentEventChangesBothHashes() {
        val first = builder.build(input(), null)
        val changed = anonymized.events.map { if (it.source.sequence == 3L) it.copy(payload = it.payload.replace("5000", "5001")) else it }
        val second = builder.build(input(events = changed), null)

        assertTrue(first.packageHash != second.packageHash)
        assertTrue(first.contentHash != second.contentHash)
    }

    @Test
    fun packageHashIsTheSha256OfTheClearZip() {
        val built = builder.build(input(), null)

        assertEquals(sha256(built.zip), built.packageHash)
        assertTrue(built.file.contentEquals(built.zip))
    }

    @Test
    fun theManifestCarriesTheContentHashOfTheOtherFourEntriesInOrder() {
        val built = builder.build(input(), null)
        val entries = unzip(built.zip)
        val manifest = Json.parseToJsonElement(text(entries, "manifest.json")).jsonObject

        assertEquals(built.contentHash, manifest.getValue("contentHash").jsonPrimitive.content)
        assertEquals(
            built.contentHash,
            DiagnosticPackageFormat.contentHash(entries.filterKeys { it != "manifest.json" }.mapValues { it.value }),
        )
    }

    @Test
    fun theManifestDescribesTheExportAndMarksAlteredEvents() {
        val entries = unzip(builder.build(input(), null).zip)
        val manifest = Json.parseToJsonElement(text(entries, "manifest.json")).jsonObject

        assertEquals("x1", manifest.getValue("exportId").jsonPrimitive.content)
        assertEquals("RECONCILIATION", manifest.getValue("scope").jsonPrimitive.content)
        assertEquals("r1", manifest.getValue("reconciliationId").jsonPrimitive.content)
        assertEquals(3, manifest.getValue("eventCount").jsonPrimitive.content.toInt())
        assertEquals("true", manifest.getValue("anonymized").jsonPrimitive.content)
        assertEquals("16", manifest.getValue("androidVersion").jsonPrimitive.content)
        assertEquals(1, manifest.getValue("projectorVersions").jsonObject.getValue("movement").jsonPrimitive.content.toInt())
        val altered = manifest.getValue("alteredEvents").jsonArray.map { it.jsonObject.getValue("sequence").jsonPrimitive.content.toLong() }
        assertEquals(listOf(1L, 2L, 3L), altered)
        assertEquals(store.events[0].hash, manifest.getValue("alteredEvents").jsonArray[0].jsonObject.getValue("originalHash").jsonPrimitive.content)
    }

    @Test
    fun eventsJsonlHasOneEventPerLineWithTheStoredPayloadVerbatim() {
        val entries = unzip(builder.build(input(), null).zip)
        val lines = text(entries, "events.jsonl").trimEnd('\n').split('\n')

        assertEquals(3, lines.size)
        val third = Json.parseToJsonElement(lines[2]).jsonObject
        assertEquals(3, third.getValue("sequence").jsonPrimitive.content.toInt())
        assertEquals("IncomeRegistered", third.getValue("type").jsonPrimitive.content)
        assertEquals(anonymized.events[2].payload, third.getValue("payload").jsonPrimitive.content)
        assertEquals(store.events[2].hash, third.getValue("hash").jsonPrimitive.content)
        assertEquals(store.events[2].previousHash, third.getValue("previousHash").jsonPrimitive.content)
    }

    @Test
    fun ledgerCsvHasAHeaderRunningBalanceAndNumbersWithoutPrefix() {
        val ledger = projections.ledger + LedgerLine(4, "m2", "a1", -250, "EXPENSE", "2026-10-03", false, null, 4_750)
        val csv = text(unzip(builder.build(input(ledger = ledger), null).zip), "ledger.csv")
        val lines = csv.trimStart('﻿').trimEnd().split("\r\n")

        assertEquals("sequence,movementId,accountId,kind,occurredOn,delta,balance,voided,voidedSequence", lines[0])
        assertEquals("3,m1,a1,INCOME,2026-10-02,50.00,50.00,false,", lines[1])
        assertEquals("4,m2,a1,EXPENSE,2026-10-03,-2.50,47.50,false,", lines[2])
    }

    @Test
    fun csvCellsThatASpreadsheetWouldReadAsAFormulaArePrefixed() {
        val hostile = projections.ledger.single().copy(movementId = "=HYPERLINK(\"http://x\")", entryKind = "+cmd")
        val csv = text(unzip(builder.build(input(ledger = listOf(hostile)), null).zip), "ledger.csv")

        assertTrue(csv.contains("'=HYPERLINK"))
        assertTrue(csv.contains("'+cmd"))
        assertFalse(csv.lines().any { it.split(',').any { cell -> cell.startsWith("=") || cell.startsWith("+") } })
    }

    @Test
    fun projectionsJsonHoldsBalancesLedgerReconciliationAndProjectorVersions() {
        val json = Json.parseToJsonElement(text(unzip(builder.build(input(), null).zip), "projections.json")).jsonObject

        assertEquals(5_000L, json.getValue("accountBalances").jsonObject.getValue("a1").jsonPrimitive.content.toLong())
        assertEquals(1, json.getValue("ledger").jsonArray.size)
        assertEquals("OPEN", json.getValue("reconciliation").jsonObject.getValue("status").jsonPrimitive.content)
        assertEquals(setOf("catalog", "movement"), json.getValue("projectorVersions").jsonObject.keys)
    }

    @Test
    fun originBalancesTravelOnlyWithTheFullScope() {
        val reconciliation = Json.parseToJsonElement(text(unzip(builder.build(input(DiagnosticScope.RECONCILIATION), null).zip), "projections.json")).jsonObject
        val history = Json.parseToJsonElement(text(unzip(builder.build(input(DiagnosticScope.ACCOUNT_HISTORY), null).zip), "projections.json")).jsonObject
        val full = Json.parseToJsonElement(text(unzip(builder.build(input(DiagnosticScope.FULL), null).zip), "projections.json")).jsonObject

        assertTrue(reconciliation["originBalances"] == null || reconciliation["originBalances"] is kotlinx.serialization.json.JsonNull)
        assertTrue(history["originBalances"] == null || history["originBalances"] is kotlinx.serialization.json.JsonNull)
        assertEquals(1, full.getValue("originBalances").jsonArray.size)
    }

    @Test
    fun noNoteAliasOrNameAppearsInAnyEntry() {
        val entries = unzip(builder.build(input(), null).zip)
        val everything = entries.values.joinToString("") { it.toString(Charsets.UTF_8) }

        listOf("BBVA", "Sueldo", "Ana", "Gómez", "sueldo de").forEach { assertFalse(it, everything.contains(it)) }
    }

    @Test
    fun withAPasswordTheZipIsInsideTheEncryptedContainerAndKeepsTheSamePackageHash() {
        val clear = builder.build(input(), null)
        val protected = builder.build(input(), password.copyOf(), random = SecureRandom())

        assertEquals(clear.packageHash, protected.packageHash)
        assertFalse(protected.file.contentEquals(clear.file))
        assertFalse(String(protected.file, Charsets.ISO_8859_1).contains("manifest.json"))
        val decrypted = EncryptedContainerReader(ByteArrayInputStream(protected.file), DiagnosticPackageFormat.MAGIC, password.copyOf()).use { it.readBytes() }
        assertEquals(clear.packageHash, sha256(decrypted))
    }

    @Test
    fun aWrongPasswordDoesNotOpenTheContainer() {
        val protected = builder.build(input(), password.copyOf())

        try {
            EncryptedContainerReader(ByteArrayInputStream(protected.file), DiagnosticPackageFormat.MAGIC, "otra clave larga".toCharArray()).use { it.readBytes() }
            fail("debió rechazar la contraseña")
        } catch (e: ContainerException) {
            assertEquals(ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED, e.reason)
        }
    }

    @Test
    fun theBuilderWipesThePasswordItWasGiven() {
        val secret = password.copyOf()

        builder.build(input(), secret)

        assertTrue(secret.all { it == '\u0000' })
    }

    @Test
    fun theManifestOfAnUnanonymizedExportHasNoAlteredEvents() {
        val plain = input().copy(anonymized = false, alteredEvents = emptyList())
        val manifest = Json.parseToJsonElement(text(unzip(builder.build(plain, null).zip), "manifest.json")).jsonObject

        assertEquals("false", manifest.getValue("anonymized").jsonPrimitive.content)
        assertEquals(0, manifest.getValue("alteredEvents").jsonArray.size)
        assertNull((manifest["alteredEvents"] as? JsonPrimitive))
        assertTrue(manifest is JsonObject)
    }
}
