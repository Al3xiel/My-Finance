package com.al3xiel.my_finances.feature.backup.domain.usecase

import com.al3xiel.my_finances.domain.usecase.RestoreMarker
import com.al3xiel.my_finances.feature.backup.domain.format.BackupImporter
import com.al3xiel.my_finances.feature.backup.domain.format.BackupFormatException
import com.al3xiel.my_finances.feature.backup.domain.format.BackupLimits
import com.al3xiel.my_finances.feature.backup.domain.format.BackupManifest
import com.al3xiel.my_finances.feature.backup.domain.format.BackupReader
import com.al3xiel.my_finances.feature.backup.domain.repository.AppRestarter
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.backup.domain.repository.DatabaseSwapper
import java.io.IOException
import javax.inject.Inject

/** Lo que se muestra antes de confirmar: cantidades y fechas, nunca montos ni nombres. */
data class BackupSummary(
    val eventCount: Long,
    val createdAt: Long,
    val appVersion: String,
    val chainIntact: Boolean,
    val firstOccurredOn: String?,
    val lastOccurredOn: String?,
)

sealed interface RestorePreview {
    data class Ready(val summary: BackupSummary) : RestorePreview

    data object Cancelled : RestorePreview

    data object NotAllowed : RestorePreview

    data object Unreadable : RestorePreview

    data class Rejected(val reason: BackupFormatException.Reason) : RestorePreview
}

sealed interface RestoreResult {
    data class Restored(val eventCount: Long) : RestoreResult

    data object Cancelled : RestoreResult

    /** La base tiene datos propios (D44). */
    data object NotAllowed : RestoreResult

    data object Unreadable : RestoreResult

    data class Rejected(val reason: BackupFormatException.Reason) : RestoreResult

    /** Falló la escritura o el intercambio; la base original sigue intacta. */
    data object Failed : RestoreResult
}

/**
 * Restaura un `.fbak` (D44, D49): descifra y verifica todo contra una base de staging, marca `RestorePending`, cambia el
 * archivo de la base y reinicia. Nada toca la base real hasta que todo el archivo verificó. Trabajo pesado: llamar fuera
 * del hilo principal. La contraseña la borra quien llama.
 */
class RestoreBackupUseCase @Inject constructor(
    private val files: BackupFilePort,
    private val canRestore: CanRestoreUseCase,
    private val importer: BackupImporter,
    private val swapper: DatabaseSwapper,
    private val marker: RestoreMarker,
    private val restarter: AppRestarter,
) {
    suspend fun preview(uri: String?, password: CharArray): RestorePreview {
        if (uri == null) return RestorePreview.Cancelled
        if (!canRestore()) return RestorePreview.NotAllowed
        return try {
            open(uri, password)?.use { RestorePreview.Ready(it.manifest.toSummary()) } ?: RestorePreview.Unreadable
        } catch (e: BackupFormatException) {
            RestorePreview.Rejected(e.reason)
        } catch (_: IOException) {
            RestorePreview.Unreadable
        }
    }

    suspend operator fun invoke(uri: String?, password: CharArray): RestoreResult {
        if (uri == null) return RestoreResult.Cancelled
        if (!canRestore()) return RestoreResult.NotAllowed
        val imported = try {
            val reader = open(uri, password) ?: return RestoreResult.Unreadable
            reader.use { importer.import(it) }
        } catch (e: BackupFormatException) {
            return RestoreResult.Rejected(e.reason)
        } catch (_: IOException) {
            return RestoreResult.Failed
        }
        marker.mark()
        try {
            swapper.swap()
        } catch (_: IOException) {
            marker.clear()
            return RestoreResult.Failed
        }
        restarter.restart()
        return RestoreResult.Restored(imported.eventCount)
    }

    private suspend fun open(uri: String, password: CharArray): BackupReader? {
        val input = files.openInput(uri) ?: return null
        return BackupReader.open(input, password, files.sizeOf(uri) ?: 0L, BackupLimits())
    }

    private fun BackupManifest.toSummary() =
        BackupSummary(eventCount, createdAt, appVersion, chainIntact, firstOccurredOn, lastOccurredOn)
}
