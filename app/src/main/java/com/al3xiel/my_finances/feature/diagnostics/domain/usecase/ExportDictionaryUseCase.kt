package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.io.readAtMost
import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.security.container.BackupPassword
import com.al3xiel.my_finances.core.security.container.ContainerHeader
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DiagnosticDictionaryExported
import com.al3xiel.my_finances.domain.model.event.DiagnosticExportCreated
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFile
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFormatException
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.FieldClassification
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.PayloadTexts
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.PlainStringFields
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.Treatment
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequestCodec
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface DictionaryPreviewResult {
    /** `pseudonyms` y `noteEventIds` son exactamente lo que se revelará; `unknown` son pedidos que este teléfono no reconoce. */
    data class Ready(val pseudonyms: List<String>, val noteEventIds: List<String>, val unknown: Int) : DictionaryPreviewResult
    data object InvalidRequest : DictionaryPreviewResult
    data object PackageMismatch : DictionaryPreviewResult
    data object NotFromReconciliation : DictionaryPreviewResult
}

sealed interface ExportDictionaryResult {
    data class Created(val revealedPseudonyms: Int, val revealedNotes: Int) : ExportDictionaryResult
    data object Cancelled : ExportDictionaryResult
    data object InvalidRequest : ExportDictionaryResult
    data object PackageMismatch : ExportDictionaryResult
    data object NotFromReconciliation : ExportDictionaryResult
    data object PasswordInvalid : ExportDictionaryResult
    data class WriteFailed(val documentDeleted: Boolean) : ExportDictionaryResult
    data class VerificationFailed(val documentDeleted: Boolean) : ExportDictionaryResult
}

/**
 * Genera el `.fkey` (04 §3.3.2) para una solicitud, solo desde la conciliación que produjo el paquete (DG7), con contraseña
 * obligatoria. Igual que el diagnóstico: escribe, relee, verifica y solo entonces emite `DiagnosticDictionaryExported` (D53).
 */
class ExportDictionaryUseCase internal constructor(
    private val eventStore: EventStore,
    private val select: SelectDiagnosticEventsUseCase,
    private val files: BackupFilePort,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val aliases: ResolveAliasesUseCase,
    private val iterations: Int,
) {
    @Inject
    constructor(
        eventStore: EventStore,
        select: SelectDiagnosticEventsUseCase,
        files: BackupFilePort,
        recorder: EventRecorder,
        ids: IdGenerator,
        aliases: ResolveAliasesUseCase,
    ) : this(eventStore, select, files, recorder, ids, aliases, ContainerHeader.DEFAULT_ITERATIONS)

    private sealed interface Resolution {
        class Found(val request: DictionaryRequest, val export: DiagnosticExportCreated, val pseudonyms: List<String>, val unknown: Int) : Resolution
        class Refused(val reason: DictionaryPreviewResult) : Resolution
    }

    /** Lo que se revelaría, para mostrarlo antes de elegir dónde guardar. */
    suspend fun preview(requestCode: String, scope: DictionaryScope, from: ReconciliationId? = null): DictionaryPreviewResult =
        when (val resolved = resolve(requestCode, scope, from)) {
        is Resolution.Found -> DictionaryPreviewResult.Ready(resolved.pseudonyms, resolved.request.eventIds, resolved.unknown)
        is Resolution.Refused -> resolved.reason
    }

    /** `uri` nulo = el usuario canceló el selector. La contraseña la borra quien llama. */
    suspend operator fun invoke(
        requestCode: String,
        scope: DictionaryScope,
        uri: String?,
        password: CharArray,
        from: ReconciliationId? = null,
    ): ExportDictionaryResult {
        if (uri == null) return ExportDictionaryResult.Cancelled
        if (BackupPassword.check(password) != null) return ExportDictionaryResult.PasswordInvalid
        val resolved = when (val r = resolve(requestCode, scope, from)) {
            is Resolution.Found -> r
            is Resolution.Refused -> return when (r.reason) {
                DictionaryPreviewResult.PackageMismatch -> ExportDictionaryResult.PackageMismatch
                DictionaryPreviewResult.NotFromReconciliation -> ExportDictionaryResult.NotFromReconciliation
                else -> ExportDictionaryResult.InvalidRequest
            }
        }
        val export = resolved.export
        val reconciliationId = checkNotNull(export.reconciliationId)
        val resolvedAliases = aliases(export.pseudonymMap.filterKeys { it in resolved.pseudonyms }, export.exportedUpToSequence)
        val notes = notesOf(resolved.request.eventIds, export) ?: return ExportDictionaryResult.PackageMismatch
        val file = DictionaryFile(
            DictionaryFormat.FORMAT_VERSION, ids.newId(), export.exportId, export.packageHash, scope, resolvedAliases, notes,
        )
        val bytes = DictionaryFormat.write(file, password, iterations)

        try {
            val output = files.openOutput(uri) ?: return ExportDictionaryResult.WriteFailed(documentDeleted = true)
            output.use { it.write(bytes) }
        } catch (_: IOException) {
            return ExportDictionaryResult.WriteFailed(files.delete(uri))
        }
        if (!verify(uri, bytes, password, file)) return ExportDictionaryResult.VerificationFailed(files.delete(uri))

        try {
            recorder.record(
                DiagnosticDictionaryExported(
                    dictionaryId = file.dictionaryId,
                    exportId = export.exportId,
                    reconciliationId = reconciliationId,
                    scope = scope,
                    revealedPseudonyms = resolvedAliases.keys.sorted(),
                    revealedNoteEventIds = notes.keys.sorted(),
                ),
            )
        } catch (e: CancellationException) {
            withContext(NonCancellable) { files.delete(uri) }
            throw e
        } catch (_: Exception) {
            return ExportDictionaryResult.WriteFailed(files.delete(uri))
        }
        return ExportDictionaryResult.Created(resolvedAliases.size, notes.size)
    }

    /** `from` = la conciliación desde la que se pide (S65): el diccionario solo sale de la que produjo el paquete (DG7). */
    private suspend fun resolve(requestCode: String, scope: DictionaryScope, from: ReconciliationId?): Resolution {
        val request = DictionaryRequestCodec.decode(requestCode) ?: return Resolution.Refused(DictionaryPreviewResult.InvalidRequest)
        val export = eventStore.readAll().asSequence().map { it.event }.filterIsInstance<DiagnosticExportCreated>()
            .firstOrNull { it.exportId == request.exportId }
        if (export == null || !export.packageHash.startsWith(request.packagePrefix)) return Resolution.Refused(DictionaryPreviewResult.PackageMismatch)
        if (export.reconciliationId == null || (from != null && export.reconciliationId != from)) return Resolution.Refused(DictionaryPreviewResult.NotFromReconciliation)
        val known = export.pseudonymMap.keys
        val chosen = if (scope == DictionaryScope.FULL) known.sorted() else request.pseudonyms.filter { it in known }.distinct().sorted()
        val unknown = if (scope == DictionaryScope.FULL) 0 else request.pseudonyms.distinct().count { it !in known }
        return Resolution.Found(request, export, chosen, unknown)
    }

    /**
     * Notas o motivos (los campos que el anonimizador elimina) de cada evento pedido, solo de los que viajaron en el paquete:
     * la solicitud es texto de fuera y no puede servir para leer notas de eventos que nunca salieron.
     */
    private suspend fun notesOf(eventIds: List<String>, export: DiagnosticExportCreated): Map<String, List<String>>? {
        if (eventIds.isEmpty()) return emptyMap()
        val selection = (select(DiagnosticSelectionRequest(export.scope, export.reconciliationId)) as? Outcome.Ok)?.value ?: return null
        val traveled = selection.events.filter { it.sequence <= export.exportedUpToSequence }.map { it.eventId }.toSet()
        val wanted = eventIds.filter { it in traveled }.toSet()
        val found = LinkedHashMap<String, List<String>>()
        for (stored in eventStore.readAll()) {
            if (stored.sequence > export.exportedUpToSequence || stored.eventId !in wanted) continue
            val texts = PlainStringFields.of(stored.event.type)
                .filter { FieldClassification.rules[it.key]?.treatment == Treatment.REMOVE }
                .flatMap { PayloadTexts.read(stored.payload, it.path) }
            if (texts.isNotEmpty()) found[stored.eventId] = texts
        }
        return found
    }

    private suspend fun verify(uri: String, expected: ByteArray, password: CharArray, file: DictionaryFile): Boolean = try {
        val input = files.openInput(uri)
        if (input == null) {
            false
        } else {
            val bytes = input.use { it.readAtMost(expected.size + 1) }
            bytes.contentEquals(expected) && DictionaryFormat.read(bytes, password) == file
        }
    } catch (_: IOException) {
        false
    } catch (_: DictionaryFormatException) {
        false
    }
}
