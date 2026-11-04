package com.al3xiel.my_finances.core.io

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** `readAtMost` reemplaza a `InputStream.readNBytes`, que no existe en Android 8 a 12 (API 26 a 32). */
class InputStreamsTest {
    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun `reads everything when the stream is shorter than the limit`() {
        val data = bytes(100)
        assertArrayEquals(data, ByteArrayInputStream(data).readAtMost(1_000))
    }

    @Test fun `stops at the limit and leaves the rest unread`() {
        val data = bytes(100)
        val input = ByteArrayInputStream(data)

        assertArrayEquals(data.copyOf(40), input.readAtMost(40))
        assertEquals(60, input.available())
    }

    @Test fun `an empty stream gives an empty array and a zero limit reads nothing`() {
        assertEquals(0, ByteArrayInputStream(ByteArray(0)).readAtMost(10).size)
        assertEquals(0, ByteArrayInputStream(bytes(10)).readAtMost(0).size)
    }

    @Test fun `limit plus one tells whether a stream is too big`() {
        assertEquals(11, ByteArrayInputStream(bytes(500)).readAtMost(11).size)
        assertEquals(10, ByteArrayInputStream(bytes(10)).readAtMost(11).size)
    }

    @Test fun `short reads from a slow stream are joined`() {
        val data = bytes(1_000)
        // Un flujo que entrega pocos bytes por llamada, como un archivo de red o un selector del sistema.
        val slow = object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (pos >= data.size) return -1
                val n = minOf(len, 7, data.size - pos)
                System.arraycopy(data, pos, b, off, n)
                pos += n
                return n
            }
        }

        assertArrayEquals(data, slow.readAtMost(5_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative limit is rejected`() {
        ByteArrayInputStream(bytes(1)).readAtMost(-1)
    }
}
