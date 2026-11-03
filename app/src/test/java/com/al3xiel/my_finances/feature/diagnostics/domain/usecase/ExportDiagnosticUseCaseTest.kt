package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

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
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DiagnosticExportCreated
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageBuilder
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticSummaryRenderer
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.ProjectionSnapshotReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDiagnosticUseCaseTest {
    private val pen = CurrencyCode.PEN
    private val store = FakeEventStore()
    private val files = FakeDiagnosticFiles()
    private val snapshots = FakeSnapshots()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val ids = SequentialIdGenerator()
    private val password = "correct horse battery".toCharArray()
    private val export = ExportDiagnosticUseCase(
        SelectDiagnosticEventsUseCase(store), EventAnonymizer(), snapshots, DiagnosticSummaryRenderer { ByteArray(8) },
        DiagnosticPackageBuilder(1_000), files, EventRecorder(store, ids, clock), clock, ids, "test", "14",
    )

    private val reconciliation = DiagnosticSelectionRequest(DiagnosticScope.RECONCILIATION, ReconciliationId("r1"))

    private suspend fun seed() {
        val day = LocalDate.of(2026, 10, 25)
        store.append(
            "c",
            listOf(
                PersonCreated(PersonId("p1"), "Ana Pérez"),
                AccountCreated(AccountId("a1"), "Cuenta Secreta", AccountType.BANK, pen, null, Money(0, pen)),
                IncomeSourceCreated(IncomeSourceId("s1"), "Empresa Secreta", PersonId("p1")),
                IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(1_000, pen), IncomeSourceId("s1"), null, "nota privada"),
                BalanceCheckRecorded(ReconciliationId("r1"), AccountId("a1"), Money(9_999, pen), Money(1_000, pen)),
            ).map { NewEvent(it, day) },
        )
    }

    private fun exports() = store.events.filter { it.event.type == "DiagnosticExportCreated" }

    private fun entries(zip: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                out[entry.name] = input.readBytes()
            }
        }
        return out
    }

    @Test
    fun writesAVerifiedFileAndOnlyThenEmitsTheAuditEvent() = runTest {
        seed()
        val result = export(reconciliation, "doc://1", null)

        val created = result as ExportDiagnosticResult.Created
        val event = exports().single().event as DiagnosticExportCreated
        assertEquals(AggregateType.RECONCILIATION, event.aggregateType)
        assertEquals("r1", event.aggregateId)
        assertTrue(event.anonymized)
        assertEquals(created.packageHash, event.packageHash)
        assertEquals(DiagnosticPackageFormat.sha256(files.files.getValue("doc://1")), event.packageHash)
        assertEquals(5L, event.exportedUpToSequence)
        assertTrue(event.pseudonymMap.isNotEmpty())
        assertEquals(event.eventCount, created.eventCount)
    }

    @Test
    fun theFileCarriesNoNameNorNote() = runTest {
        seed()
        export(reconciliation, "doc://1", null)

        val events = String(entries(files.files.getValue("doc://1")).getValue(DiagnosticPackageFormat.EVENTS), Charsets.UTF_8)
        listOf("Ana", "Secreta", "nota privada").forEach { assertFalse(it, events.contains(it)) }
        assertTrue(events.contains("Account-A"))
    }

    @Test
    fun withoutAnonymizingTheNamesTravelAndNoPseudonymsAreRecorded() = runTest {
        seed()
        export(reconciliation, "doc://1", null, anonymize = false)

        val events = String(entries(files.files.getValue("doc://1")).getValue(DiagnosticPackageFormat.EVENTS), Charsets.UTF_8)
        assertTrue(events.contains("Cuenta Secreta"))
        val event = exports().single().event as DiagnosticExportCreated
        assertFalse(event.anonymized)
        assertTrue(event.pseudonymMap.isEmpty())
    }

    @Test
    fun cancellingThePickerEmitsNothing() = runTest {
        seed()
        assertEquals(ExportDiagnosticResult.Cancelled, export(reconciliation, null, null))
        assertEquals(0, files.opened)
        assertTrue(exports().isEmpty())
    }

    @Test
    fun anUnknownReconciliationIsNotFoundAndWritesNothing() = runTest {
        seed()
        val result = export(DiagnosticSelectionRequest(DiagnosticScope.RECONCILIATION, ReconciliationId("nope")), "doc://1", null)
        assertEquals(ExportDiagnosticResult.NotFound, result)
        assertEquals(0, files.opened)
        assertTrue(exports().isEmpty())
    }

    @Test
    fun aFailedWriteDeletesTheDocumentAndEmitsNothing() = runTest {
        seed()
        files.failWrite = true
        assertEquals(ExportDiagnosticResult.WriteFailed(documentDeleted = true), export(reconciliation, "doc://1", null))
        assertNull(files.files["doc://1"])
        assertTrue(exports().isEmpty())
    }

    @Test
    fun aFailedVerificationDeletesTheDocumentAndEmitsNothing() = runTest {
        seed()
        files.corruptOnRead = true
        assertEquals(ExportDiagnosticResult.VerificationFailed(documentDeleted = true), export(reconciliation, "doc://1", null))
        assertNull(files.files["doc://1"])
        assertTrue(exports().isEmpty())
    }

    @Test
    fun theProjectionsTravelCutAtTheSelectionNotAtToday() = runTest {
        seed()
        export(reconciliation, "doc://1", null)
        assertEquals(5L, snapshots.cutAsked?.upToSequence)
    }

    @Test
    fun ifTheAuditEventCannotBeSavedTheFileIsDeleted() = runTest {
        seed()
        val failing = object : EventStore by store {
            override suspend fun append(correlationId: String, events: List<NewEvent>): List<StoredEvent> = throw IOException("disk full")
        }
        val useCase = ExportDiagnosticUseCase(
            SelectDiagnosticEventsUseCase(store), EventAnonymizer(), snapshots, DiagnosticSummaryRenderer { ByteArray(8) },
            DiagnosticPackageBuilder(1_000), files, EventRecorder(failing, ids, clock), clock, ids, "test", "14",
        )
        assertEquals(ExportDiagnosticResult.WriteFailed(documentDeleted = true), useCase(reconciliation, "doc://1", null))
        assertNull(files.files["doc://1"])
        assertTrue(exports().isEmpty())
    }

    @Test
    fun theFullScopeNeedsNoReconciliationAndBelongsToTheSystem() = runTest {
        seed()
        export(DiagnosticSelectionRequest(DiagnosticScope.FULL), "doc://1", null)

        val event = exports().single().event as DiagnosticExportCreated
        assertEquals(AggregateType.SYSTEM, event.aggregateType)
        assertNull(event.reconciliationId)
        assertEquals(true, snapshots.includeOriginAsked)
    }

    @Test
    fun withAPasswordTheFileIsEncryptedAndTheHashIsOfThePlainZip() = runTest {
        seed()
        val mine = password.copyOf()
        export(reconciliation, "doc://1", mine)

        val file = files.files.getValue("doc://1")
        assertFalse(file[0] == 'P'.code.toByte() && file[1] == 'K'.code.toByte())
        val zip = EncryptedContainerReader(ByteArrayInputStream(file), DiagnosticPackageFormat.MAGIC, password).use { it.readBytes() }
        assertEquals((exports().single().event as DiagnosticExportCreated).packageHash, DiagnosticPackageFormat.sha256(zip))
        assertEquals(DiagnosticPackageFormat.ENTRIES.toSet(), entries(zip).keys)
        assertArrayEquals("the caller owns and wipes its password", mine, password)
    }

    @Test
    fun aWrongPasswordCannotOpenTheFile() = runTest {
        seed()
        export(reconciliation, "doc://1", password.copyOf())
        val failure = runCatching {
            EncryptedContainerReader(ByteArrayInputStream(files.files.getValue("doc://1")), DiagnosticPackageFormat.MAGIC, "other password!!".toCharArray())
                .use { it.readBytes() }
        }.exceptionOrNull()
        assertEquals(ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED, (failure as ContainerException).reason)
    }
}
