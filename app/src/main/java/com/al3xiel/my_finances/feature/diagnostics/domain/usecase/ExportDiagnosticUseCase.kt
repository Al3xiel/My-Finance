package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.io.readAtMost
import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.security.container.EncryptedContainerReader
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.DiagnosticExportCreated
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.domain.model.BuiltPackage
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticPackageWriter
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.AnonymizationResult
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.AnonymizedEvent
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageInput
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSummary
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticSummaryRenderer
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.ProjectionSnapshotReader
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Named

sealed interface ExportDiagnosticResult {
    data class Created(val eventCount: Long, val packageHash: String, val passwordProtected: Boolean) : ExportDiagnosticResult

    /** El usuario cerró el selector sin elegir dónde guardar. */
    data object Cancelled : ExportDiagnosticResult

    data object NotFound : ExportDiagnosticResult

    data class WriteFailed(val documentDeleted: Boolean) : ExportDiagnosticResult

    data class VerificationFailed(val documentDeleted: Boolean) : ExportDiagnosticResult
}

/**
 * Exporta un paquete `.fdiag` (04 §3.3.1): elige los eventos, los anonimiza, junta lo que dicen las proyecciones, lo empaqueta,
 * lo escribe en el documento elegido, lo vuelve a leer y solo entonces emite `DiagnosticExportCreated` (D53). Si algo falla
 * intenta borrar el documento a medias. La re-autenticación la pide el ViewModel; la contraseña la borra quien llama.
 */
class ExportDiagnosticUseCase @Inject constructor(
    private val select: SelectDiagnosticEventsUseCase,
    private val anonymizer: EventAnonymizer,
    private val snapshots: ProjectionSnapshotReader,
    private val renderer: DiagnosticSummaryRenderer,
    private val builder: DiagnosticPackageWriter,
    private val files: BackupFilePort,
    private val recorder: EventRecorder,
    private val clock: Clock,
    private val ids: IdGenerator,
    @Named("appVersion") private val appVersion: String,
    @Named("androidVersion") private val androidVersion: String,
) {
    /** `uri` nulo = el usuario canceló el selector. Con `anonymize = false` los nombres viajan tal cual. */
    suspend operator fun invoke(
        request: DiagnosticSelectionRequest,
        uri: String?,
        password: CharArray?,
        anonymize: Boolean = true,
    ): ExportDiagnosticResult {
        if (uri == null) return ExportDiagnosticResult.Cancelled
        val selection = when (val chosen = select(request)) {
            is Outcome.Ok -> chosen.value
            is Outcome.Failed -> return ExportDiagnosticResult.NotFound
        }
        val result = if (anonymize) {
            anonymizer.anonymize(selection.events)
        } else {
            AnonymizationResult(selection.events.map { AnonymizedEvent(it, it.payload) }, emptyMap(), emptyList())
        }
        val projections = snapshots.read(selection.accountId, selection.reconciliationId, includeOrigin = request.scope == DiagnosticScope.FULL, cut = selection.cut)
        val exportedAt = Instant.now(clock)
        val summary = renderer.render(
            DiagnosticSummary(
                scope = request.scope,
                anonymized = anonymize,
                eventCount = selection.eventCount,
                firstOccurredOn = selection.firstOccurredOn,
                lastOccurredOn = selection.lastOccurredOn,
                exportedAt = exportedAt,
                reconciliation = projections.reconciliation,
                accountCount = projections.accountBalances.size,
            ),
        )
        val exportId = ids.newId()
        val built = builder.build(
            DiagnosticPackageInput(
                exportId = exportId,
                scope = request.scope,
                reconciliationId = selection.reconciliationId,
                anonymized = anonymize,
                exportedUpToSequence = selection.exportedUpToSequence,
                exportedAt = exportedAt,
                appVersion = appVersion,
                androidVersion = androidVersion,
                events = result.events,
                alteredEvents = result.altered,
                projections = projections,
                summaryPdf = summary,
            ),
            // El constructor borra su copia; la contraseña de quien llama sigue viva para releer el archivo.
            password?.copyOf(),
        )

        try {
            val output = files.openOutput(uri) ?: return ExportDiagnosticResult.WriteFailed(documentDeleted = true)
            output.use { it.write(built.file) }
        } catch (_: IOException) {
            return ExportDiagnosticResult.WriteFailed(files.delete(uri))
        }
        if (!verifyWritten(uri, built, password)) return ExportDiagnosticResult.VerificationFailed(files.delete(uri))

        try {
            recorder.record(
                DiagnosticExportCreated(
                    exportId = exportId,
                    reconciliationId = selection.reconciliationId,
                    scope = request.scope,
                    anonymized = anonymize,
                    exportedUpToSequence = selection.exportedUpToSequence,
                    pseudonymMap = result.pseudonymMap,
                    eventCount = selection.eventCount.toLong(),
                    packageHash = built.packageHash,
                ),
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) { files.delete(uri) }
            throw e
        } catch (_: Exception) {
            return ExportDiagnosticResult.WriteFailed(files.delete(uri))
        }
        return ExportDiagnosticResult.Created(selection.eventCount.toLong(), built.packageHash, passwordProtected = password != null)
    }

    /** Relee el documento: mismos bytes y, con contraseña, que esa contraseña lo abre y da el ZIP esperado. */
    private suspend fun verifyWritten(uri: String, built: BuiltPackage, password: CharArray?): Boolean = try {
        val expected = built.file.size
        val input = files.openInput(uri)
        if (input == null || (files.sizeOf(uri) ?: expected.toLong()) != expected.toLong()) {
            input?.close()
            false
        } else {
            val bytes = input.use { it.readAtMost(expected + 1) }
            bytes.contentEquals(built.file) && (password == null || opensToExpectedZip(bytes, built, password))
        }
    } catch (_: IOException) {
        false
    }

    private fun opensToExpectedZip(bytes: ByteArray, built: BuiltPackage, password: CharArray): Boolean =
        EncryptedContainerReader(ByteArrayInputStream(bytes), DiagnosticPackageFormat.MAGIC, password)
            .use { DiagnosticPackageFormat.sha256(it.readBytes()) == built.packageHash }
}
