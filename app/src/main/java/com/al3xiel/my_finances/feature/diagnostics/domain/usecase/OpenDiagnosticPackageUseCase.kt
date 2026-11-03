package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageException
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageImporter
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceEventWriter
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceMeta
import java.io.IOException
import javax.inject.Inject

sealed interface OpenPackageResult {
    data class Opened(val exportId: String, val eventCount: Int, val packageHash: String) : OpenPackageResult

    /** El usuario cerró el selector sin elegir un archivo. */
    data object Cancelled : OpenPackageResult

    /** Ya hay un paquete en el espacio: hay que borrarlo antes de abrir otro. */
    data object AlreadyOpen : OpenPackageResult

    /** Solo el motivo: nunca el contenido del archivo. */
    data class Rejected(val reason: DiagnosticPackageException.Reason) : OpenPackageResult

    data object ReadFailed : OpenPackageResult
}

/**
 * Abre un `.fdiag` en el espacio de diagnóstico (D50, DG5). El archivo se valida entero antes de escribir nada; si la
 * escritura falla se borra el espacio, para no dejar un paquete a medias. Debe llamarse fuera del hilo principal.
 */
class OpenDiagnosticPackageUseCase @Inject constructor(
    private val files: BackupFilePort,
    private val importer: DiagnosticPackageImporter,
    private val workspace: DiagnosticWorkspace,
    private val writer: WorkspaceEventWriter,
) {
    /** `uri` nulo = el usuario canceló el selector. La contraseña la borra quien llama. */
    suspend operator fun invoke(uri: String?, password: CharArray?): OpenPackageResult {
        if (uri == null) return OpenPackageResult.Cancelled
        if (workspace.hasPackage()) return OpenPackageResult.AlreadyOpen
        val parsed = try {
            val input = files.openInput(uri) ?: return OpenPackageResult.ReadFailed
            input.use { importer.read(it, files.sizeOf(uri) ?: 0L, password) }
        } catch (e: DiagnosticPackageException) {
            return OpenPackageResult.Rejected(e.reason)
        } catch (_: IOException) {
            return OpenPackageResult.ReadFailed
        }
        try {
            writer.write(parsed.events)
            workspace.save(WorkspaceMeta(parsed.manifest, parsed.projections, parsed.packageHash))
        } catch (_: Exception) {
            workspace.delete()
            return OpenPackageResult.ReadFailed
        }
        return OpenPackageResult.Opened(parsed.manifest.exportId, parsed.events.size, parsed.packageHash)
    }
}
