package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageException
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticPackageReader
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticWorkspaceStore
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceEventWriter
import com.al3xiel.my_finances.feature.diagnostics.domain.model.WorkspaceMeta
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
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
 * escritura falla (o se cancela) se borra el espacio, para no dejar un paquete a medias. Debe llamarse fuera del hilo principal.
 *
 * Un espacio ocupado sin metadato legible se descarta antes de abrir. Eso incluye uno cuya clave se perdió o cuyo sellado se
 * alteró: es una copia de un paquete ajeno que se puede volver a abrir desde su archivo. Las aperturas no se solapan porque la
 * pantalla las serializa (una sola a la vez); el uso concurrente desde otro lugar no está previsto.
 */
class OpenDiagnosticPackageUseCase @Inject constructor(
    private val files: BackupFilePort,
    private val importer: DiagnosticPackageReader,
    private val workspace: DiagnosticWorkspaceStore,
    private val writer: WorkspaceEventWriter,
) {
    /** `uri` nulo = el usuario canceló el selector. La contraseña la borra quien llama. */
    suspend operator fun invoke(uri: String?, password: CharArray?): OpenPackageResult {
        if (uri == null) return OpenPackageResult.Cancelled
        // Un espacio ocupado cuyo metadato no se puede abrir (se cortó una apertura, o se perdió la clave) no sirve para nada:
        // se descarta para que no quede un paquete de un tercero imposible de ver y de borrar.
        if (workspace.hasPackage() && workspace.meta() == null) workspace.delete()
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
        } catch (e: CancellationException) {
            workspace.delete()
            throw e
        } catch (_: Exception) {
            workspace.delete()
            return OpenPackageResult.ReadFailed
        }
        return OpenPackageResult.Opened(parsed.manifest.exportId, parsed.events.size, parsed.packageHash)
    }
}
