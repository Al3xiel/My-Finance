package com.al3xiel.my_finances.core.security.container

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class EncryptedContainerTest {

    private val magic = "TEST".toByteArray()
    private val password = "correct horse battery".toCharArray()
    private val iterations = 1_000
    private val smallBlock = 64
    private val headerSize = ContainerHeader.SIZE
    private val encryptedSmallBlock = smallBlock + ContainerHeader.TAG_SIZE

    private fun seal(data: ByteArray, blockSize: Int = smallBlock, pass: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        EncryptedContainerWriter(out, magic, pass, iterations, blockSize).use { it.write(data) }
        return out.toByteArray()
    }

    private fun open(
        bytes: ByteArray,
        pass: CharArray = password,
        limits: ContainerLimits = ContainerLimits(),
    ): ByteArray = EncryptedContainerReader(ByteArrayInputStream(bytes), magic, pass, limits).use { it.readBytes() }

    private fun reasonOf(block: () -> Unit): ContainerException.Reason {
        try {
            block()
        } catch (e: ContainerException) {
            return e.reason
        }
        fail("expected a ContainerException")
        error("unreachable")
    }

    private fun data(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

    private fun ByteArray.flip(index: Int) = copyOf().also { it[index] = (it[index].toInt() xor 0x01).toByte() }

    private fun block(sealed: ByteArray, index: Int) =
        sealed.copyOfRange(headerSize + index * encryptedSmallBlock, headerSize + (index + 1) * encryptedSmallBlock)

    @Test
    fun roundTripsSmallData() {
        val plain = data(10)
        assertArrayEquals(plain, open(seal(plain)))
    }

    @Test
    fun roundTripsEmptyData() {
        assertArrayEquals(ByteArray(0), open(seal(ByteArray(0))))
    }

    @Test
    fun roundTripsSeveralBlocksAndExactMultiples() {
        for (size in listOf(smallBlock - 1, smallBlock, smallBlock + 1, smallBlock * 4, 1_000)) {
            val plain = data(size)
            assertArrayEquals("size $size", plain, open(seal(plain)))
        }
    }

    @Test
    fun roundTripsWithTheDefaultBlockSize() {
        val plain = data(200_000)
        assertArrayEquals(plain, open(seal(plain, ContainerHeader.BLOCK_SIZE)))
    }

    @Test
    fun sealingTwiceNeverRepeatsSaltNorCiphertext() {
        val plain = data(100)
        val a = seal(plain)
        val b = seal(plain)
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOfRange(10, 26).contentEquals(b.copyOfRange(10, 26)))
    }

    @Test
    fun wrongPasswordIsReportedAsWrongPasswordOrTampered() {
        val sealed = seal(data(300))
        assertEquals(
            ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED,
            reasonOf { open(sealed, "another password!".toCharArray()) },
        )
    }

    @Test
    fun aFileWithAnotherMagicIsNotAContainer() {
        val sealed = seal(data(30)).flip(0)
        assertEquals(ContainerException.Reason.NOT_A_CONTAINER, reasonOf { open(sealed) })
        assertEquals(ContainerException.Reason.NOT_A_CONTAINER, reasonOf { open(ByteArray(5)) })
        assertEquals(ContainerException.Reason.NOT_A_CONTAINER, reasonOf { open(ByteArray(0)) })
    }

    @Test
    fun aNewerVersionIsRejectedBeforeDerivingTheKey() {
        val sealed = seal(data(30)).also { it[4] = (ContainerHeader.VERSION + 1).toByte() }
        assertEquals(ContainerException.Reason.UNSUPPORTED_VERSION, reasonOf { open(sealed) })
    }

    @Test
    fun excessiveKdfIterationsAreRejectedBeforeDerivingTheKey() {
        val sealed = seal(data(30))
        val huge = ContainerHeader.MAX_ITERATIONS + 1
        for (i in 0 until 4) sealed[6 + i] = (huge ushr (24 - 8 * i)).toByte()
        assertEquals(ContainerException.Reason.LIMITS_EXCEEDED, reasonOf { open(sealed) })
    }

    @Test
    fun anyAlteredHeaderByteFailsTheOpening() {
        val sealed = seal(data(300))
        for (index in 4 until headerSize) {
            val failed = try {
                open(sealed.flip(index))
                false
            } catch (_: ContainerException) {
                true
            }
            assertEquals("header byte $index", true, failed)
        }
    }

    @Test
    fun anAlteredFirstBlockIsWrongPasswordOrTampered() {
        val sealed = seal(data(300))
        assertEquals(ContainerException.Reason.WRONG_PASSWORD_OR_TAMPERED, reasonOf { open(sealed.flip(headerSize + 3)) })
    }

    @Test
    fun anAlteredLaterBlockIsTamperedOrTruncated() {
        val sealed = seal(data(300))
        assertEquals(
            ContainerException.Reason.TAMPERED_OR_TRUNCATED,
            reasonOf { open(sealed.flip(headerSize + encryptedSmallBlock + 5)) },
        )
    }

    @Test
    fun droppingTheLastBlockIsDetected() {
        val sealed = seal(data(200))
        val truncated = sealed.copyOfRange(0, headerSize + 3 * encryptedSmallBlock)
        assertEquals(ContainerException.Reason.TAMPERED_OR_TRUNCATED, reasonOf { open(truncated) })
    }

    @Test
    fun truncatingInTheMiddleOfABlockIsDetected() {
        val sealed = seal(data(200))
        assertEquals(ContainerException.Reason.TAMPERED_OR_TRUNCATED, reasonOf { open(sealed.copyOfRange(0, sealed.size - 3)) })
    }

    @Test
    fun aFileWithoutAnyBlockIsDetected() {
        val sealed = seal(data(200))
        assertEquals(ContainerException.Reason.TAMPERED_OR_TRUNCATED, reasonOf { open(sealed.copyOfRange(0, headerSize)) })
    }

    @Test
    fun reorderedBlocksAreDetected() {
        val sealed = seal(data(200))
        val reordered = sealed.copyOfRange(0, headerSize) + block(sealed, 0) + block(sealed, 2) + block(sealed, 1) +
            sealed.copyOfRange(headerSize + 3 * encryptedSmallBlock, sealed.size)
        assertEquals(ContainerException.Reason.TAMPERED_OR_TRUNCATED, reasonOf { open(reordered) })
    }

    @Test
    fun anExtraBlockAppendedAtTheEndIsDetected() {
        val sealed = seal(data(200))
        assertEquals(ContainerException.Reason.TAMPERED_OR_TRUNCATED, reasonOf { open(sealed + block(sealed, 1)) })
    }

    @Test
    fun moreBlocksThanTheLimitAreRejected() {
        val sealed = seal(data(200))
        assertEquals(ContainerException.Reason.LIMITS_EXCEEDED, reasonOf { open(sealed, limits = ContainerLimits(maxBlocks = 2)) })
    }

    @Test
    fun aBlockSizeOutsideTheAllowedRangeIsRejected() {
        val sealed = seal(data(30))
        val huge = ContainerHeader.BLOCK_SIZE + 1
        for (i in 0 until 4) sealed[headerSize - 4 + i] = (huge ushr (24 - 8 * i)).toByte()
        assertEquals(ContainerException.Reason.LIMITS_EXCEEDED, reasonOf { open(sealed) })
    }
}
