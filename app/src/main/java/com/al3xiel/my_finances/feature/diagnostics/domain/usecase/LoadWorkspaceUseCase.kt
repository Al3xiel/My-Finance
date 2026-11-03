package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspacePseudonymSource
import javax.inject.Inject

/** Resumen del paquete abierto (S64): de dónde viene y cuánto trae; nunca nombres. */
data class WorkspaceSummary(
    val exportId: String,
    val scope: String,
    val anonymized: Boolean,
    val eventCount: Int,
    val exportedAt: String,
    val appVersion: String,
    val androidVersion: String,
    val alteredEventCount: Int,
    val packageHashPrefix: String,
    val pseudonyms: List<String>,
)

class LoadWorkspaceUseCase @Inject constructor(
    private val workspace: DiagnosticWorkspace,
    private val pseudonyms: WorkspacePseudonymSource,
) {
    /** `null` si no hay paquete abierto o su metadato no se puede leer. */
    suspend operator fun invoke(): WorkspaceSummary? {
        val meta = workspace.meta() ?: return null
        val manifest = meta.manifest
        return WorkspaceSummary(
            exportId = manifest.exportId,
            scope = manifest.scope,
            anonymized = manifest.anonymized,
            eventCount = manifest.eventCount,
            exportedAt = manifest.exportedAt,
            appVersion = manifest.appVersion,
            androidVersion = manifest.androidVersion,
            alteredEventCount = manifest.alteredEvents.size,
            packageHashPrefix = meta.packageHash.take(CreateDictionaryRequestUseCase.PREFIX),
            pseudonyms = pseudonyms.pseudonyms(),
        )
    }
}

/** Borra el espacio de diagnóstico completo: base, auxiliares, clave, alias y archivos sellados. */
class DeleteDiagnosticWorkspaceUseCase @Inject constructor(private val workspace: DiagnosticWorkspace) {
    operator fun invoke() = workspace.delete()
}
