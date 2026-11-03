package com.al3xiel.my_finances.core.security.container

import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Contenedor cifrado genérico (`.fbak`, y luego `.fdiag` y `.fkey`): cabecera en claro + bloques AES-256-GCM.
 * Nonce = prefijo aleatorio de 8 bytes + contador de 4 bytes; datos asociados = cabecera + índice + marca de último.
 * Clave: PBKDF2-HMAC-SHA256 con sal nueva en cada archivo, así que un nonce nunca se repite con la misma clave.
 */
internal object ContainerCrypto {
    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally {
            spec.clearPassword()
        }
    }

    fun cipher(mode: Int, key: SecretKeySpec, header: ContainerHeader, index: Int, last: Boolean): Cipher {
        val nonce = ByteArray(12)
        header.noncePrefix.copyInto(nonce, 0)
        for (i in 0 until 4) nonce[8 + i] = (index ushr (24 - 8 * i)).toByte()
        val aad = ByteArray(ContainerHeader.SIZE + 5)
        header.raw.copyInto(aad, 0)
        for (i in 0 until 4) aad[ContainerHeader.SIZE + i] = (index ushr (24 - 8 * i)).toByte()
        aad[ContainerHeader.SIZE + 4] = if (last) 1 else 0
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(ContainerHeader.TAG_SIZE * 8, nonce))
            updateAAD(aad)
        }
    }
}

class EncryptedContainerWriter(
    private val output: OutputStream,
    magic: ByteArray,
    password: CharArray,
    iterations: Int = ContainerHeader.DEFAULT_ITERATIONS,
    private val blockSize: Int = ContainerHeader.BLOCK_SIZE,
    random: SecureRandom = SecureRandom(),
) : OutputStream() {
    private val header: ContainerHeader
    private val key: SecretKeySpec
    private val buffer: ByteArray
    private var filled = 0
    private var index = 0
    private var closed = false

    init {
        require(iterations in ContainerHeader.MIN_ITERATIONS..ContainerHeader.MAX_ITERATIONS)
        require(blockSize in ContainerHeader.MIN_BLOCK_SIZE..ContainerHeader.BLOCK_SIZE)
        val salt = ByteArray(ContainerHeader.SALT_SIZE).also(random::nextBytes)
        val prefix = ByteArray(ContainerHeader.NONCE_PREFIX_SIZE).also(random::nextBytes)
        header = ContainerHeader.build(magic, iterations, salt, prefix, blockSize)
        key = ContainerCrypto.deriveKey(password, salt, iterations)
        buffer = ByteArray(blockSize)
        output.write(header.raw)
    }

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(source: ByteArray, offset: Int, length: Int) {
        check(!closed) { "closed" }
        var position = offset
        var remaining = length
        while (remaining > 0) {
            // Solo se sella un bloque lleno cuando llegan más datos: así el último se sabe al cerrar.
            if (filled == blockSize) emit(last = false)
            val n = minOf(remaining, blockSize - filled)
            source.copyInto(buffer, filled, position, position + n)
            filled += n
            position += n
            remaining -= n
        }
    }

    override fun flush() = output.flush()

    override fun close() {
        if (closed) return
        closed = true
        try {
            emit(last = true)
            output.flush()
        } finally {
            output.close()
        }
    }

    private fun emit(last: Boolean) {
        if (index == Int.MAX_VALUE) throw ContainerException(ContainerException.Reason.LIMITS_EXCEEDED)
        val cipher = ContainerCrypto.cipher(Cipher.ENCRYPT_MODE, key, header, index, last)
        output.write(cipher.doFinal(buffer, 0, filled))
        index++
        filled = 0
    }
}

class EncryptedContainerReader(
    input: InputStream,
    magic: ByteArray,
    password: CharArray,
    private val limits: ContainerLimits = ContainerLimits(),
) : InputStream() {
    val header: ContainerHeader
    private val source: PushbackInputStream = PushbackInputStream(input, 1)
    private val key: SecretKeySpec
    private var plain = ByteArray(0)
    private var position = 0
    private var index = 0
    private var finished = false

    init {
        header = ContainerHeader.read(source, magic)
        key = ContainerCrypto.deriveKey(password, header.salt, header.iterations)
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        while (position >= plain.size) {
            if (finished) return -1
            nextBlock()
        }
        val n = minOf(length, plain.size - position)
        plain.copyInto(target, offset, position, position + n)
        position += n
        return n
    }

    override fun close() = source.close()

    private fun nextBlock() {
        if (index >= limits.maxBlocks) throw ContainerException(ContainerException.Reason.LIMITS_EXCEEDED)
        val full = header.blockSize + ContainerHeader.TAG_SIZE
        val chunk = ByteArray(full)
        val n = ContainerHeader.readFully(source, chunk)
        if (n == 0) throw ContainerException(ContainerException.Reason.TAMPERED_OR_TRUNCATED)
        val last = if (n < full) {
            true
        } else {
            val probe = source.read()
            if (probe < 0) true else { source.unread(probe); false }
        }
        if (n < ContainerHeader.TAG_SIZE) throw failure()
        plain = try {
            ContainerCrypto.cipher(Cipher.DECRYPT_MODE, key, header, index, last).doFinal(chunk, 0, n)
        } catch (_: AEADBadTagException) {
            throw failure()
        } catch (_: GeneralSecurityException) {
            throw failure()
        }
        position = 0
        index++
        finished = last
    }

    private fun failure() = ContainerException(
        if (index == 0) ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED else ContainerException.Reason.TAMPERED_OR_TRUNCATED,
    )
}
