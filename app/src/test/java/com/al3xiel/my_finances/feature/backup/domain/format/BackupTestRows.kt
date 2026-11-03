package com.al3xiel.my_finances.feature.backup.domain.format

import com.al3xiel.my_finances.core.security.container.EncryptedContainerWriter
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.model.event.EventHasher
import com.al3xiel.my_finances.domain.model.event.HashedFields
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

val TEST_PASSWORD = "correct horse battery".toCharArray()
const val TEST_ITERATIONS = 1_000

/** Cadena válida de `AccountRenamed`; `type` y `schemaVersion` se pueden forzar para probar los rechazos. */
fun testChain(
    size: Int,
    type: String = "AccountRenamed",
    schemaVersion: Int = 1,
    payload: (Int) -> String = { EventCodec.encode(AccountRenamed(AccountId("a1"), "n$it")) },
): List<ChainRow> {
    val rows = mutableListOf<ChainRow>()
    var previous = EventHasher.GENESIS
    for (i in 1..size) {
        val day = "2026-10-%02d".format((i % 28) + 1)
        val fields = HashedFields(previous, "e$i", type, schemaVersion, "ACCOUNT", "a1", "c", payload(i), day, 1_000L + i, "test")
        val hash = EventHasher.hash(fields)
        rows += ChainRow(i.toLong(), hash, fields)
        previous = hash
    }
    return rows
}

fun manifestFor(
    rows: List<ChainRow>,
    chainIntact: Boolean = true,
    eventCount: Long = rows.size.toLong(),
    lastHash: String = rows.lastOrNull()?.hash ?: EventHasher.GENESIS,
    maxEventSchemaVersion: Int = 1,
) = BackupManifest(
    backupId = "b1",
    createdAt = 5_000L,
    appVersion = "test",
    dbSchemaVersion = 6,
    maxEventSchemaVersion = maxEventSchemaVersion,
    eventCount = eventCount,
    lastHash = lastHash,
    chainIntact = chainIntact,
    firstOccurredOn = rows.firstOrNull()?.fields?.occurredOn,
    lastOccurredOn = rows.lastOrNull()?.fields?.occurredOn,
)

fun writeBackup(manifest: BackupManifest, rows: List<ChainRow>, password: CharArray = TEST_PASSWORD): ByteArray {
    val out = ByteArrayOutputStream()
    BackupWriter(out, password, manifest, TEST_ITERATIONS).use { it.append(rows) }
    return out.toByteArray()
}

/** Archivo con líneas arbitrarias (JSON roto, líneas gigantes…), cifrado y comprimido como el real. */
fun craftBackup(lines: List<String>, password: CharArray = TEST_PASSWORD): ByteArray {
    val out = ByteArrayOutputStream()
    EncryptedContainerWriter(out, BackupFormat.MAGIC, password, TEST_ITERATIONS).use { container ->
        GZIPOutputStream(container).use { gzip -> lines.forEach { gzip.write((it + "\n").toByteArray()) } }
    }
    return out.toByteArray()
}

fun manifestLine(manifest: BackupManifest): String = Json.encodeToString(manifest)
