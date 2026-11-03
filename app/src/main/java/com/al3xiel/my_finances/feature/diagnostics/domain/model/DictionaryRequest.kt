package com.al3xiel.my_finances.feature.diagnostics.domain.model

import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * "Solicitud de diccionario" (D55): lo que quien audita pide a quien exportó. Lleva el paquete (`exportId` y un prefijo de su
 * hash), los seudónimos que quiere conocer y los `eventId` de las notas que quiere leer. Nunca lleva nombres.
 */
@Serializable
data class DictionaryRequest(
    val exportId: String,
    val packagePrefix: String,
    val pseudonyms: List<String>,
    val eventIds: List<String>,
)

/** Código de texto: base64url del JSON, un punto y 8 caracteres de suma de verificación (SHA-256 del JSON). */
object DictionaryRequestCodec {
    private const val MAX_LENGTH = 40_000
    private const val CHECKSUM_CHARS = 8
    private val json = Json

    fun encode(request: DictionaryRequest): String {
        val bytes = json.encodeToString(DictionaryRequest.serializer(), request).toByteArray(Charsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "." + checksum(bytes)
    }

    /** `null` si el código está incompleto, alterado o no es una solicitud. */
    fun decode(text: String): DictionaryRequest? = runCatching {
        val code = text.trim()
        require(code.length in 1..MAX_LENGTH)
        val payload = code.substringBefore('.')
        val sum = code.substringAfter('.', "")
        val bytes = Base64.getUrlDecoder().decode(payload)
        require(sum == checksum(bytes))
        json.decodeFromString(DictionaryRequest.serializer(), String(bytes, Charsets.UTF_8))
    }.getOrNull()

    private fun checksum(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(CHECKSUM_CHARS)
}
