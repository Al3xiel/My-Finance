package com.al3xiel.my_finances.feature.backup.domain.format

import com.al3xiel.my_finances.core.security.container.ContainerHeader
import com.al3xiel.my_finances.core.security.container.EncryptedContainerWriter
import com.al3xiel.my_finances.domain.model.event.ChainRow
import kotlinx.serialization.encodeToString
import java.io.Closeable
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.GZIPOutputStream

/**
 * Escribe un `.fbak`: contenedor cifrado → gzip → una línea JSON por fila, con el manifiesto primero.
 * Las filas llegan por lotes: nada de esto exige tener el registro entero en memoria. [close] termina el último bloque.
 */
class BackupWriter(
    output: OutputStream,
    password: CharArray,
    manifest: BackupManifest,
    iterations: Int = ContainerHeader.DEFAULT_ITERATIONS,
    random: SecureRandom = SecureRandom(),
) : Closeable {
    private val gzip = GZIPOutputStream(
        EncryptedContainerWriter(output, BackupFormat.MAGIC, password, iterations = iterations, random = random),
    )

    init {
        writeLine(BackupFormat.json.encodeToString(manifest))
    }

    fun append(rows: List<ChainRow>) {
        rows.forEach { writeLine(BackupFormat.json.encodeToString(BackupRow.of(it))) }
    }

    override fun close() = gzip.close()

    private fun writeLine(line: String) {
        gzip.write(line.toByteArray(Charsets.UTF_8))
        gzip.write('\n'.code)
    }
}
