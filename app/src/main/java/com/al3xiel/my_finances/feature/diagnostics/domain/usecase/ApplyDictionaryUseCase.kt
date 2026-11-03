package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.security.container.BackupPassword
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFile
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFormatException
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticWorkspaceStore
import java.io.IOException
import javax.inject.Inject

sealed interface ApplyDictionaryResult {
    data class Applied(val revealedPseudonyms: Int, val revealedNotes: Int) : ApplyDictionaryResult
    data object Cancelled : ApplyDictionaryResult
    data object NoPackage : ApplyDictionaryResult
    data object NotADictionary : ApplyDictionaryResult
    data object WrongPassword : ApplyDictionaryResult

    /** El diccionario es de otra exportación: no coinciden `exportId` y `packageHash`. */
    data object Mismatch : ApplyDictionaryResult
    data object ReadFailed : ApplyDictionaryResult
}

/**
 * Aplica un `.fkey` solo dentro del espacio de diagnóstico, y solo si coinciden `exportId` y `packageHash` del paquete abierto
 * (DG6). Queda sellado con Keystore en el espacio y se borra con él. La contraseña la borra quien llama.
 */
class ApplyDictionaryUseCase @Inject constructor(
    private val files: BackupFilePort,
    private val workspace: DiagnosticWorkspaceStore,
) {
    suspend operator fun invoke(uri: String?, password: CharArray): ApplyDictionaryResult {
        if (uri == null) return ApplyDictionaryResult.Cancelled
        val meta = workspace.meta() ?: return ApplyDictionaryResult.NoPackage
        val bytes = try {
            val input = files.openInput(uri) ?: return ApplyDictionaryResult.ReadFailed
            input.use { it.readNBytes(MAX_BYTES + 1) }
        } catch (_: IOException) {
            return ApplyDictionaryResult.ReadFailed
        }
        if (bytes.size > MAX_BYTES) return ApplyDictionaryResult.NotADictionary
        val file = try {
            DictionaryFormat.read(bytes, password)
        } catch (e: DictionaryFormatException) {
            return when (e.reason) {
                DictionaryFormatException.Reason.WRONG_PASSWORD -> ApplyDictionaryResult.WrongPassword
                else -> ApplyDictionaryResult.NotADictionary
            }
        }
        if (file.exportId != meta.manifest.exportId || file.packageHash != meta.packageHash) return ApplyDictionaryResult.Mismatch
        workspace.saveDictionary(DiagnosticPackageFormat.json.encodeToString(DictionaryFile.serializer(), file).toByteArray(Charsets.UTF_8))
        return ApplyDictionaryResult.Applied(file.aliases.size, file.notes.size)
    }

    /** El diccionario aplicado al paquete abierto, si hay. */
    fun applied(): DictionaryFile? = workspace.dictionary()?.let {
        runCatching { DiagnosticPackageFormat.json.decodeFromString(DictionaryFile.serializer(), String(it, Charsets.UTF_8)) }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
