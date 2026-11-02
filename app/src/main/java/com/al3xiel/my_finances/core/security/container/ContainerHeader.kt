package com.al3xiel.my_finances.core.security.container

import java.io.IOException
import java.io.InputStream

/** Fallo del contenedor. El mensaje es solo el motivo: nunca contraseñas, claves ni contenido. */
class ContainerException(val reason: Reason) : IOException(reason.name) {
    enum class Reason {
        NOT_A_CONTAINER,
        UNSUPPORTED_VERSION,
        LIMITS_EXCEEDED,

        /** El primer bloque no abrió: no se puede distinguir una contraseña errónea de un archivo alterado. */
        WRONG_PASSWORD_OR_TAMPERED,

        /** Un bloque posterior no abrió, falta el último o sobra uno. */
        TAMPERED_OR_TRUNCATED,
    }
}

/** Topes sobre un archivo que no es de confianza; se validan antes y durante el descifrado. */
data class ContainerLimits(val maxBlocks: Long = DEFAULT_MAX_BLOCKS) {
    companion object {
        /** 1 048 576 bloques de 64 KiB = 64 GiB: más que cualquier historial real. */
        const val DEFAULT_MAX_BLOCKS = 1L shl 20
    }
}

/**
 * Cabecera en claro (se autentica como datos asociados de cada bloque):
 * magic(4) · versión(1) · KDF(1) · iteraciones(4) · sal(16) · prefijo de nonce(8) · tamaño de bloque(4).
 */
class ContainerHeader(
    val iterations: Int,
    val salt: ByteArray,
    val noncePrefix: ByteArray,
    val blockSize: Int,
    internal val raw: ByteArray,
) {
    companion object {
        const val SIZE = 38
        const val VERSION = 1
        const val KDF_PBKDF2_SHA256 = 1
        const val SALT_SIZE = 16
        const val NONCE_PREFIX_SIZE = 8
        const val TAG_SIZE = 16
        const val BLOCK_SIZE = 64 * 1024
        const val MIN_BLOCK_SIZE = 16
        const val MIN_ITERATIONS = 1_000
        const val DEFAULT_ITERATIONS = 600_000
        const val MAX_ITERATIONS = 2_000_000

        fun build(magic: ByteArray, iterations: Int, salt: ByteArray, noncePrefix: ByteArray, blockSize: Int): ContainerHeader {
            require(magic.size == 4 && salt.size == SALT_SIZE && noncePrefix.size == NONCE_PREFIX_SIZE)
            val raw = ByteArray(SIZE)
            magic.copyInto(raw, 0)
            raw[4] = VERSION.toByte()
            raw[5] = KDF_PBKDF2_SHA256.toByte()
            putInt(raw, 6, iterations)
            salt.copyInto(raw, 10)
            noncePrefix.copyInto(raw, 26)
            putInt(raw, 34, blockSize)
            return ContainerHeader(iterations, salt, noncePrefix, blockSize, raw)
        }

        /** Lee y valida la cabecera sin derivar la clave: sirve para mostrar un resumen o rechazar a tiempo. */
        fun read(input: InputStream, magic: ByteArray): ContainerHeader {
            val raw = ByteArray(SIZE)
            if (readFully(input, raw) != SIZE) throw ContainerException(ContainerException.Reason.NOT_A_CONTAINER)
            if (!raw.copyOfRange(0, 4).contentEquals(magic)) throw ContainerException(ContainerException.Reason.NOT_A_CONTAINER)
            if (raw[4].toInt() != VERSION || raw[5].toInt() != KDF_PBKDF2_SHA256) {
                throw ContainerException(ContainerException.Reason.UNSUPPORTED_VERSION)
            }
            val iterations = getInt(raw, 6)
            val blockSize = getInt(raw, 34)
            if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS || blockSize !in MIN_BLOCK_SIZE..BLOCK_SIZE) {
                throw ContainerException(ContainerException.Reason.LIMITS_EXCEEDED)
            }
            return ContainerHeader(iterations, raw.copyOfRange(10, 26), raw.copyOfRange(26, 34), blockSize, raw)
        }

        internal fun readFully(input: InputStream, buffer: ByteArray): Int {
            var total = 0
            while (total < buffer.size) {
                val n = input.read(buffer, total, buffer.size - total)
                if (n < 0) break
                total += n
            }
            return total
        }

        private fun putInt(target: ByteArray, offset: Int, value: Int) {
            for (i in 0 until 4) target[offset + i] = (value ushr (24 - 8 * i)).toByte()
        }

        private fun getInt(source: ByteArray, offset: Int): Int {
            var value = 0
            for (i in 0 until 4) value = (value shl 8) or (source[offset + i].toInt() and 0xFF)
            return value
        }
    }
}
