package com.al3xiel.my_finances
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageException

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.di.DATABASE_NAME
import com.al3xiel.my_finances.core.id.UuidIdGenerator
import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.security.AndroidKeystoreKeyWrapper
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventStoreImpl
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.data.projection.ProjectorSetFactory
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DiagnosticExportCreated
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.domain.model.event.EntityType
import com.al3xiel.my_finances.domain.model.event.IncomeRegistered
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageBuilder
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageFormat
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageImporter
import com.al3xiel.my_finances.feature.diagnostics.data.PdfDiagnosticSummaryRenderer
import com.al3xiel.my_finances.feature.diagnostics.data.RoomProjectionSnapshotReader
import com.al3xiel.my_finances.feature.diagnostics.data.RoomWorkspaceReplayer
import com.al3xiel.my_finances.feature.diagnostics.data.WorkspaceProjector
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceEventSink
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ApplyDictionaryResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ApplyDictionaryUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.BuildDivergenceReportUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.CreateDictionaryRequestUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.CreateRequestResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDictionaryResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDictionaryUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.OpenDiagnosticPackageUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.OpenPackageResult
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ReportOutcome
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ResolveAliasesUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.SelectDiagnosticEventsUseCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private class DiagnosticMemoryFiles : BackupFilePort {
    val files = mutableMapOf<String, ByteArray>()

    override suspend fun openOutput(uri: String): OutputStream {
        val buffer = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) = buffer.write(b)
            override fun write(source: ByteArray, offset: Int, length: Int) = buffer.write(source, offset, length)
            override fun close() {
                files[uri] = buffer.toByteArray()
            }
        }
    }

    override suspend fun openInput(uri: String): InputStream? = files[uri]?.let { ByteArrayInputStream(it) }
    override suspend fun sizeOf(uri: String): Long? = files[uri]?.size?.toLong()
    override suspend fun delete(uri: String) = files.remove(uri) != null
}

/**
 * Ida y vuelta con una base real: se exporta una conciliación con diferencia (con y sin contraseña), se abre en el espacio de
 * diagnóstico (SQLCipher y Keystore reales), el reporte coincide con lo exportado, el paquete no lleva alias ni notas, un
 * diccionario parcial revela solo lo pedido y el borrado no deja archivos ni toca la base real.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticRoundTripTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pen = CurrencyCode.PEN
    private val clock = Clock.systemUTC()
    private val ids = UuidIdGenerator()
    private val files = DiagnosticMemoryFiles()
    private val directory = requireNotNull(context.getDatabasePath("diagnostic-workspace.db").absoluteFile.parentFile)
    private val keyFile = File(context.noBackupFilesDir, "diagnostic-roundtrip.key")
    private val password = "correct horse battery".toCharArray()
    private lateinit var live: AppDatabase
    private lateinit var store: EventStoreImpl
    private lateinit var workspace: DiagnosticWorkspace
    private lateinit var recorder: EventRecorder

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        live = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        store = EventStoreImpl(live, ProjectorSetFactory(live).create(), ids, clock, "test")
        recorder = EventRecorder(store, ids, clock)
        workspace = DiagnosticWorkspace(
            WorkspaceFiles(directory), WorkspaceKeys(AndroidKeystoreKeyWrapper("diagnostic_roundtrip_wrapper"), keyFile, SecureRandom()),
        ) { key ->
            Room.databaseBuilder(context, AppDatabase::class.java, "diagnostic-workspace.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addCallback(EventTriggersCallback)
                .build()
        }
        workspace.delete()
        runBlocking { seed() }
    }

    @After
    fun tearDown() {
        workspace.delete()
        live.close()
    }

    /** Una cuenta con un ingreso y una conciliación cuya diferencia son 100 céntimos. */
    private suspend fun seed() {
        store.append(
            "seed",
            listOf(
                PersonCreated(PersonId("p1"), "Ana Pérez"),
                AccountCreated(AccountId("a1"), "Cuenta Secreta", AccountType.BANK, pen, null, Money(500, pen)),
                IncomeSourceCreated(IncomeSourceId("s1"), "Empresa Secreta", PersonId("p1")),
                IncomeRegistered(MovementId("m1"), AccountId("a1"), Money(1_000, pen), IncomeSourceId("s1"), null, "nota privada"),
                BalanceCheckRecorded(ReconciliationId("r1"), AccountId("a1"), Money(1_600, pen), Money(1_500, pen)),
            ).map { NewEvent(it, LocalDate.of(2026, 10, 25)) },
        )
    }

    private val export get() = ExportDiagnosticUseCase(
        SelectDiagnosticEventsUseCase(store), EventAnonymizer(), RoomProjectionSnapshotReader(live), PdfDiagnosticSummaryRenderer(context),
        DiagnosticPackageBuilder(1_000), files, recorder, clock, ids, "test", "14",
    )

    private val open get() = OpenDiagnosticPackageUseCase(files, DiagnosticPackageImporter(), workspace, WorkspaceEventSink(workspace))
    private val report get() = BuildDivergenceReportUseCase(workspace, RoomWorkspaceReplayer(workspace, WorkspaceProjector(clock, "test")))

    private suspend fun exportReconciliation(secret: CharArray?) =
        export(DiagnosticSelectionRequest(DiagnosticScope.RECONCILIATION, ReconciliationId("r1")), "doc://pkg", secret?.copyOf())

    private fun events(zip: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.name == DiagnosticPackageFormat.EVENTS) return String(input.readBytes(), Charsets.UTF_8)
            }
        }
        error("no events entry")
    }

    private fun exportEvent() = runBlocking { store.readAll() }.map { it.event }.filterIsInstance<DiagnosticExportCreated>().single()

    @Test
    fun exportOpenAndCompareWithoutPassword() = runBlocking {
        val realFile = context.getDatabasePath(DATABASE_NAME)
        val realBefore = realFile.takeIf { it.exists() }?.lastModified()
        assertTrue(exportReconciliation(null) is ExportDiagnosticResult.Created)

        val plain = files.files.getValue("doc://pkg")
        val text = events(plain)
        listOf("Ana", "Secreta", "nota privada").forEach { assertFalse("$it travels in the package", text.contains(it)) }

        assertTrue(open("doc://pkg", null) is OpenPackageResult.Opened)
        val result = (report() as ReportOutcome.Ready).report
        assertFalse("the replay matches what was exported: $result", result.diverges)
        assertEquals(1_500L, result.balances.single().recalculatedMinor)
        assertEquals(result.balances.single().exportedMinor, result.balances.single().recalculatedMinor)
        assertEquals(false, result.reconciliation?.diverges)

        assertEquals(realBefore, realFile.takeIf { it.exists() }?.lastModified())
    }

    /** Lo que pasa de verdad: se exporta con la conciliación abierta y después (o antes) hubo otros movimientos de la cuenta. */
    @Test
    fun aMovementAfterTheCheckThatIsNotLinkedDoesNotCauseAFalseDivergence() = runBlocking {
        store.append(
            "later",
            listOf(
                IncomeRegistered(MovementId("m2"), AccountId("a1"), Money(400, pen), IncomeSourceId("s1"), null, "otra nota"),
            ).map { NewEvent(it, LocalDate.of(2026, 10, 26)) },
        )
        assertEquals(1_900L, live.ledgerQueryDao().balance("a1"))

        assertTrue(exportReconciliation(null) is ExportDiagnosticResult.Created)
        assertTrue(open("doc://pkg", null) is OpenPackageResult.Opened)

        val result = (report() as ReportOutcome.Ready).report
        assertFalse("the later movement is not in the package and must not look like a divergence: $result", result.diverges)
        assertEquals(1_500L, result.balances.single().exportedMinor)
        assertEquals(1_500L, result.balances.single().recalculatedMinor)
    }

    @Test
    fun exportOpenAndCompareWithPassword() = runBlocking {
        assertTrue(exportReconciliation(password) is ExportDiagnosticResult.Created)
        val file = files.files.getValue("doc://pkg")
        assertFalse(file[0] == 'P'.code.toByte() && file[1] == 'K'.code.toByte())

        assertEquals(OpenPackageResult.Rejected(com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageException.Reason.PASSWORD_REQUIRED), open("doc://pkg", null))
        assertFalse(workspace.hasPackage())
        assertTrue(open("doc://pkg", password.copyOf()) is OpenPackageResult.Opened)
        assertFalse((report() as ReportOutcome.Ready).report.diverges)
    }

    @Test
    fun aPartialDictionaryRevealsOnlyWhatWasAsked() = runBlocking {
        exportReconciliation(null)
        open("doc://pkg", null)
        val pseudonyms = workspace.pseudonyms()
        val export = exportEvent()
        val accountPseudonym = export.pseudonymMap.entries.first { it.value.entityType == EntityType.ACCOUNT }.key
        assertTrue("$accountPseudonym not in $pseudonyms; map=${export.pseudonymMap}", accountPseudonym in pseudonyms)

        val code = (CreateDictionaryRequestUseCase(workspace)(listOf(accountPseudonym), emptyList()) as CreateRequestResult.Ready).code
        val dictionary = ExportDictionaryUseCase(store, SelectDiagnosticEventsUseCase(store), files, recorder, ids, ResolveAliasesUseCase(store))
        val created = dictionary(code, DictionaryScope.PARTIAL, "doc://key", password.copyOf(), ReconciliationId("r1"))
        assertEquals(ExportDictionaryResult.Created(revealedPseudonyms = 1, revealedNotes = 0), created)

        assertEquals(ApplyDictionaryResult.Applied(1, 0), ApplyDictionaryUseCase(files, workspace)("doc://key", password.copyOf()))
        val applied = ApplyDictionaryUseCase(files, workspace).applied()
        assertEquals(mapOf(accountPseudonym to "Cuenta Secreta"), applied?.aliases)
        assertFalse(applied?.aliases.orEmpty().values.any { it.contains("Ana") || it.contains("Empresa") })
    }

    @Test
    fun deletingTheWorkspaceLeavesNothingBehindAndTheRealDataIntact() = runBlocking {
        exportReconciliation(null)
        open("doc://pkg", null)
        report()
        val liveBalance = live.ledgerQueryDao().balance("a1")
        val liveEvents = live.eventDao().count()

        workspace.delete()

        assertFalse(workspace.hasPackage())
        assertEquals(emptyList<String>(), directory.list().orEmpty().filter { it.startsWith("diagnostic-workspace") })
        assertFalse(keyFile.exists())
        assertNull(workspace.meta())
        assertEquals(liveBalance, live.ledgerQueryDao().balance("a1"))
        assertEquals(liveEvents, live.eventDao().count())
    }
}
