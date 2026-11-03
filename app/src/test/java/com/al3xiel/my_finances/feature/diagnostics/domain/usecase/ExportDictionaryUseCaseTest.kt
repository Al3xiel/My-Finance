package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DiagnosticDictionaryExported
import com.al3xiel.my_finances.domain.model.event.DiagnosticExportCreated
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.domain.model.event.EntityType
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageBuilder
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequestCodec
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticSummaryRenderer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDictionaryUseCaseTest {
    private val pen = CurrencyCode.PEN
    private val store = FakeEventStore()
    private val files = FakeDiagnosticFiles()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val ids = SequentialIdGenerator()
    private val password = "correct horse battery".toCharArray()
    private val recorder = EventRecorder(store, ids, clock)
    private val dictionary = ExportDictionaryUseCase(store, SelectDiagnosticEventsUseCase(store), files, recorder, ids, ResolveAliasesUseCase(store), iterations = 1_000)

    private suspend fun seedAndExport(scope: DiagnosticScope = DiagnosticScope.RECONCILIATION): DiagnosticExportCreated {
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
        val export = ExportDiagnosticUseCase(
            SelectDiagnosticEventsUseCase(store), EventAnonymizer(), FakeSnapshots(), DiagnosticSummaryRenderer { ByteArray(4) },
            DiagnosticPackageBuilder(1_000), FakeDiagnosticFiles(), recorder, clock, ids, "test", "14",
        )
        export(DiagnosticSelectionRequest(scope, ReconciliationId("r1").takeIf { scope == DiagnosticScope.RECONCILIATION }), "doc://pkg", null)
        // Un renombre posterior a la exportación no debe filtrarse al diccionario.
        store.append("c2", listOf(NewEvent(AccountRenamed(AccountId("a1"), "Nombre Nuevo"), day)))
        return store.events.first { it.event.type == "DiagnosticExportCreated" }.event as DiagnosticExportCreated
    }

    private fun pseudonymOf(export: DiagnosticExportCreated, type: EntityType) =
        export.pseudonymMap.entries.first { it.value.entityType == type }.key

    private fun code(export: DiagnosticExportCreated, pseudonyms: List<String>, eventIds: List<String> = emptyList()) =
        DictionaryRequestCodec.encode(DictionaryRequest(export.exportId, export.packageHash.take(12), pseudonyms, eventIds))

    private fun exportedEvents() = store.events.filter { it.event.type == "DiagnosticDictionaryExported" }

    @Test
    fun aPartialDictionaryRevealsOnlyWhatWasRequestedAsTheyWereAtTheExport() = runTest {
        val export = seedAndExport()
        val account = pseudonymOf(export, EntityType.ACCOUNT)

        val result = dictionary(code(export, listOf(account)), DictionaryScope.PARTIAL, "doc://key", password.copyOf())

        assertEquals(ExportDictionaryResult.Created(revealedPseudonyms = 1, revealedNotes = 0), result)
        val content = DictionaryFormat.read(files.files.getValue("doc://key"), password.copyOf())
        assertEquals(mapOf(account to "Cuenta Secreta"), content.aliases)
        assertEquals(export.exportId, content.exportId)
        assertEquals(export.packageHash, content.packageHash)
        val event = exportedEvents().single().event as DiagnosticDictionaryExported
        assertEquals(listOf(account), event.revealedPseudonyms)
        assertEquals(ReconciliationId("r1"), event.reconciliationId)
        assertEquals(DictionaryScope.PARTIAL, event.scope)
    }

    @Test
    fun theFullScopeRevealsEveryPseudonymOfThePackage() = runTest {
        val export = seedAndExport()
        val result = dictionary(code(export, emptyList()), DictionaryScope.FULL, "doc://key", password.copyOf())
        assertEquals(export.pseudonymMap.size, (result as ExportDictionaryResult.Created).revealedPseudonyms)
        assertEquals(export.pseudonymMap.keys, DictionaryFormat.read(files.files.getValue("doc://key"), password.copyOf()).aliases.keys)
    }

    @Test
    fun aRequestedNoteIsRevealedByItsEventId() = runTest {
        val export = seedAndExport()
        val income = store.events.first { it.event.type == "IncomeRegistered" }.eventId
        val result = dictionary(code(export, emptyList(), listOf(income)), DictionaryScope.PARTIAL, "doc://key", password.copyOf())

        assertEquals(1, (result as ExportDictionaryResult.Created).revealedNotes)
        assertEquals(listOf("nota privada"), DictionaryFormat.read(files.files.getValue("doc://key"), password.copyOf()).notes.getValue(income))
        assertEquals(listOf(income), (exportedEvents().single().event as DiagnosticDictionaryExported).revealedNoteEventIds)
    }

    @Test
    fun aNoteOfAnEventThatNeverTraveledInThePackageIsNotRevealed() = runTest {
        val day = LocalDate.of(2026, 10, 26)
        store.append("other", listOf(NewEvent(IncomeRegistered(MovementId("m9"), AccountId("a9"), Money(5, pen), IncomeSourceId("s9"), null, "nota ajena"), day)))
        val outside = store.events.first { it.event.type == "IncomeRegistered" }.eventId
        val export = seedAndExport()

        val result = dictionary(code(export, emptyList(), listOf(outside)), DictionaryScope.PARTIAL, "doc://key", password.copyOf())

        assertEquals(ExportDictionaryResult.Created(revealedPseudonyms = 0, revealedNotes = 0), result)
        assertTrue(DictionaryFormat.read(files.files.getValue("doc://key"), password).notes.isEmpty())
    }

    @Test
    fun ifTheAuditEventCannotBeSavedTheDictionaryFileIsDeleted() = runTest {
        val export = seedAndExport()
        val failing = object : EventStore by store {
            override suspend fun append(correlationId: String, events: List<NewEvent>): List<StoredEvent> = throw java.io.IOException("disk full")
        }
        val useCase = ExportDictionaryUseCase(store, SelectDiagnosticEventsUseCase(store), files, EventRecorder(failing, ids, clock), ids, ResolveAliasesUseCase(store), iterations = 1_000)

        val result = useCase(code(export, emptyList()), DictionaryScope.FULL, "doc://key", password.copyOf())

        assertEquals(ExportDictionaryResult.WriteFailed(documentDeleted = true), result)
        assertNull(files.files["doc://key"])
        assertTrue(exportedEvents().isEmpty())
    }

    @Test
    fun ifTheEventsOfThePackageCannotBeSelectedAgainTheNotesAreRefusedInsteadOfSilentlyEmpty() = runTest {
        val export = seedAndExport()
        val income = store.events.first { it.event.type == "IncomeRegistered" }.eventId
        val unreadable = object : EventStore by store {
            override suspend fun readAll(): List<StoredEvent> = throw java.io.IOException("unreadable")
        }
        val useCase = ExportDictionaryUseCase(store, SelectDiagnosticEventsUseCase(unreadable), files, recorder, ids, ResolveAliasesUseCase(store), iterations = 1_000)

        val result = useCase(code(export, emptyList(), listOf(income)), DictionaryScope.PARTIAL, "doc://key", password.copyOf())

        assertEquals(ExportDictionaryResult.PackageMismatch, result)
        assertNull(files.files["doc://key"])
        assertTrue(exportedEvents().isEmpty())
    }

    @Test
    fun theFileIsEncryptedAndCarriesNoPlainName() = runTest {
        val export = seedAndExport()
        dictionary(code(export, listOf(pseudonymOf(export, EntityType.ACCOUNT))), DictionaryScope.PARTIAL, "doc://key", password.copyOf())
        assertFalse(String(files.files.getValue("doc://key"), Charsets.ISO_8859_1).contains("Secreta"))
    }

    @Test
    fun aPackageNotMadeFromAReconciliationHasNoDictionary() = runTest {
        val export = seedAndExport(DiagnosticScope.FULL)
        assertNull(export.reconciliationId)
        val result = dictionary(code(export, emptyList()), DictionaryScope.FULL, "doc://key", password.copyOf())
        assertEquals(ExportDictionaryResult.NotFromReconciliation, result)
        assertTrue(exportedEvents().isEmpty())
        assertEquals(0, files.opened)
    }

    @Test
    fun aRequestForAnotherPackageIsRefused() = runTest {
        val export = seedAndExport()
        val other = DictionaryRequestCodec.encode(DictionaryRequest(export.exportId, "000000000000", emptyList(), emptyList()))
        assertEquals(ExportDictionaryResult.PackageMismatch, dictionary(other, DictionaryScope.FULL, "doc://key", password.copyOf()))
        val unknown = DictionaryRequestCodec.encode(DictionaryRequest("nope", export.packageHash.take(12), emptyList(), emptyList()))
        assertEquals(ExportDictionaryResult.PackageMismatch, dictionary(unknown, DictionaryScope.FULL, "doc://key", password.copyOf()))
        assertTrue(exportedEvents().isEmpty())
    }

    @Test
    fun aRequestAskedFromAnotherReconciliationIsRefused() = runTest {
        val export = seedAndExport()
        val result = dictionary(code(export, emptyList()), DictionaryScope.FULL, "doc://key", password.copyOf(), from = ReconciliationId("r2"))
        assertEquals(ExportDictionaryResult.NotFromReconciliation, result)
        assertTrue(dictionary.preview(code(export, emptyList()), DictionaryScope.FULL, from = ReconciliationId("r1")) is DictionaryPreviewResult.Ready)
        assertTrue(exportedEvents().isEmpty())
    }

    @Test
    fun aCorruptCodeIsInvalid() = runTest {
        val export = seedAndExport()
        val broken = code(export, emptyList()).dropLast(2) + "xx"
        assertEquals(ExportDictionaryResult.InvalidRequest, dictionary(broken, DictionaryScope.FULL, "doc://key", password.copyOf()))
    }

    @Test
    fun theDictionaryAlwaysNeedsAPassword() = runTest {
        val export = seedAndExport()
        val result = dictionary(code(export, emptyList()), DictionaryScope.FULL, "doc://key", "short".toCharArray())
        assertEquals(ExportDictionaryResult.PasswordInvalid, result)
        assertTrue(exportedEvents().isEmpty())
    }

    @Test
    fun cancellingThePickerEmitsNothing() = runTest {
        val export = seedAndExport()
        assertEquals(ExportDictionaryResult.Cancelled, dictionary(code(export, emptyList()), DictionaryScope.FULL, null, password.copyOf()))
        assertTrue(exportedEvents().isEmpty())
    }

    @Test
    fun aFailedVerificationDeletesTheFileAndEmitsNothing() = runTest {
        val export = seedAndExport()
        files.corruptOnRead = true
        val result = dictionary(code(export, emptyList()), DictionaryScope.FULL, "doc://key", password.copyOf())
        assertEquals(ExportDictionaryResult.VerificationFailed(documentDeleted = true), result)
        assertTrue(exportedEvents().isEmpty())
    }

    @Test
    fun thePreviewListsExactlyWhatWillBeRevealed() = runTest {
        val export = seedAndExport()
        val account = pseudonymOf(export, EntityType.ACCOUNT)
        val preview = dictionary.preview(code(export, listOf(account, "Account-ZZ")), DictionaryScope.PARTIAL) as DictionaryPreviewResult.Ready
        assertEquals(listOf(account), preview.pseudonyms)
        assertEquals(1, preview.unknown)
    }
}
