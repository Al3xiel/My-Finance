package com.al3xiel.my_finances.core.io

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Lee a lo sumo `limit` bytes y se detiene en el fin del flujo. Reemplaza a `InputStream.readNBytes`, que no existe antes de
 * Android 13 (API 33) y hacía fallar la lectura de archivos en teléfonos con Android 8 a 12. Leer `limit + 1` sirve para saber
 * si el flujo pasa del tope sin cargarlo entero.
 */
fun InputStream.readAtMost(limit: Int): ByteArray {
    require(limit >= 0) { "limit must not be negative" }
    val out = ByteArrayOutputStream(minOf(limit, BUFFER_SIZE))
    val buffer = ByteArray(minOf(maxOf(limit, 1), BUFFER_SIZE))
    var remaining = limit
    while (remaining > 0) {
        val read = read(buffer, 0, minOf(buffer.size, remaining))
        if (read < 0) break
        out.write(buffer, 0, read)
        remaining -= read
    }
    return out.toByteArray()
}

private const val BUFFER_SIZE = 8 * 1024
