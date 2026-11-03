package com.al3xiel.my_finances.feature.diagnostics.data

import com.al3xiel.my_finances.core.security.container.ContainerException
import com.al3xiel.my_finances.core.security.container.ContainerHeader
import com.al3xiel.my_finances.core.security.container.EncryptedContainerReader
import com.al3xiel.my_finances.core.security.container.EncryptedContainerWriter
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.SecureRandom
import kotlinx.serialization.Serializable

/** Contenido de un `.fkey`: qué paquete revela y los alias reales. Vive cifrado con la contraseña (DG4, DG6). */
@Serializable
data class DictionaryFile(
    val formatVersion: Int,
    val dictionaryId: String,
    val exportId: String,
    val packageHash: String,
    val scope: DictionaryScope,
    /** seudónimo → alias real. */
    val aliases: Map<String, String>,
    /** `eventId` → textos de las notas o motivos de ese evento. */
    val notes: Map<String, List<String>>,
)

class DictionaryFormatException(val reason: Reason) : IOException(reason.name) {
    enum class Reason { NOT_A_DICTIONARY, WRONG_PASSWORD, MALFORMED, NEWER_VERSION, TOO_LARGE }
}

/** Formato `.fkey`: el JSON de [DictionaryFile] dentro del contenedor cifrado. Ninguna función borra la contraseña recibida. */
object DictionaryFormat {
    val MAGIC = byteArrayOf('F'.code.toByte(), 'K'.code.toByte(), 'E'.code.toByte(), 'Y'.code.toByte())
    const val FORMAT_VERSION = 1
    private const val MAX_BYTES = 8L * 1024 * 1024

    fun write(
        file: DictionaryFile,
        password: CharArray,
        iterations: Int = ContainerHeader.DEFAULT_ITERATIONS,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptedContainerWriter(out, MAGIC, password, iterations = iterations, random = random).use {
            it.write(DiagnosticPackageFormat.json.encodeToString(DictionaryFile.serializer(), file).toByteArray(Charsets.UTF_8))
        }
        return out.toByteArray()
    }

    fun read(bytes: ByteArray, password: CharArray): DictionaryFile {
        if (bytes.size > MAX_BYTES) throw DictionaryFormatException(DictionaryFormatException.Reason.TOO_LARGE)
        val plain = try {
            EncryptedContainerReader(ByteArrayInputStream(bytes), MAGIC, password).use { it.readBytes() }
        } catch (e: ContainerException) {
            throw DictionaryFormatException(
                when (e.reason) {
                    ContainerException.Reason.NOT_A_CONTAINER -> DictionaryFormatException.Reason.NOT_A_DICTIONARY
                    ContainerException.Reason.UNSUPPORTED_VERSION -> DictionaryFormatException.Reason.NEWER_VERSION
                    ContainerException.Reason.LIMITS_EXCEEDED -> DictionaryFormatException.Reason.TOO_LARGE
                    ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED, ContainerException.Reason.TAMPERED_OR_TRUNCATED ->
                        DictionaryFormatException.Reason.WRONG_PASSWORD
                },
            )
        }
        val file = try {
            DiagnosticPackageFormat.json.decodeFromString(DictionaryFile.serializer(), String(plain, Charsets.UTF_8))
        } catch (_: Exception) {
            throw DictionaryFormatException(DictionaryFormatException.Reason.MALFORMED)
        }
        if (file.formatVersion > FORMAT_VERSION) throw DictionaryFormatException(DictionaryFormatException.Reason.NEWER_VERSION)
        return file
    }
}
