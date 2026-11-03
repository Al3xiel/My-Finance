package com.al3xiel.my_finances.feature.diagnostics.data

import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Una línea de `events.jsonl`: el registro tal como se guardó, con el payload como texto para no alterar lo que cubre el hash. */
@Serializable
data class PackageEventLine(
    val sequence: Long,
    val eventId: String,
    val type: String,
    val schemaVersion: Int,
    val aggregateType: String,
    val aggregateId: String,
    val correlationId: String,
    val payload: String,
    val occurredOn: String,
    val recordedAt: Long,
    val appVersion: String,
    val previousHash: String,
    val hash: String,
)

@Serializable
data class PackageAlteredEvent(val sequence: Long, val originalHash: String, val fields: List<String>)

/** `manifest.json`. El hash del ZIP no puede ir aquí (sería circular): el manifiesto lleva el de las otras cuatro entradas. */
@Serializable
data class PackageManifest(
    val formatVersion: Int,
    val exportId: String,
    val appVersion: String,
    val androidVersion: String,
    val exportedAt: String,
    val scope: String,
    val reconciliationId: String?,
    val anonymized: Boolean,
    val exportedUpToSequence: Long,
    val eventCount: Int,
    val projectorVersions: Map<String, Int>,
    val alteredEvents: List<PackageAlteredEvent>,
    val contentHash: String,
)

/** Formato `.fdiag` (04 §3.3.1, D54): ZIP de cinco entradas fijas, con o sin el contenedor cifrado alrededor. */
object DiagnosticPackageFormat {
    val MAGIC = byteArrayOf('F'.code.toByte(), 'D'.code.toByte(), 'I'.code.toByte(), 'G'.code.toByte())

    const val FORMAT_VERSION = 1
    const val SUMMARY = "summary.pdf"
    const val LEDGER = "ledger.csv"
    const val EVENTS = "events.jsonl"
    const val PROJECTIONS = "projections.json"
    const val MANIFEST = "manifest.json"

    /** Orden fijo del ZIP; el manifiesto va al final porque lleva el hash de las anteriores. */
    val ENTRIES = listOf(SUMMARY, LEDGER, EVENTS, PROJECTIONS, MANIFEST)

    /** Mismo contenido, mismo ZIP: todas las entradas llevan esta fecha (hora local del DOS, sin zona). */
    const val ENTRY_YEAR = 1980

    val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    /** SHA-256 de las entradas dadas en el orden recibido; cada una entra con su nombre y su largo para que no se confundan. */
    fun contentHash(entries: Map<String, ByteArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((name, bytes) in entries) {
            digest.update(name.toByteArray(Charsets.UTF_8))
            digest.update(0)
            val length = bytes.size.toLong()
            for (shift in 56 downTo 0 step 8) digest.update((length ushr shift).toByte())
            digest.update(bytes)
        }
        return hex(digest.digest())
    }

    fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
