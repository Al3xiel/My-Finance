package com.al3xiel.my_finances.feature.backup.data.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupLimitsTest {
    private fun open(bytes: ByteArray, limits: BackupLimits) =
        BackupReader.open(ByteArrayInputStream(bytes), TEST_PASSWORD, bytes.size.toLong(), limits)

    private fun reasonOf(block: () -> Unit) = assertThrows(BackupFormatException::class.java, block).reason

    @Test
    fun fileLargerThanTheLimitIsRejectedBeforeReading() {
        val rows = testChain(1)
        val bytes = writeBackup(manifestFor(rows), rows)
        assertEquals(BackupFormatException.Reason.TOO_LARGE, reasonOf { open(bytes, BackupLimits(maxFileBytes = 10)) })
    }

    @Test
    fun manifestAnnouncingTooManyEventsIsRejectedBeforeReadingRows() {
        val rows = testChain(1)
        val bytes = writeBackup(manifestFor(rows, eventCount = 5_000), rows)
        assertEquals(BackupFormatException.Reason.TOO_MANY_EVENTS, reasonOf { open(bytes, BackupLimits(maxEvents = 100)) })
    }

    @Test
    fun moreRowsThanTheManifestSaysAreRejected() {
        val rows = testChain(3)
        val bytes = writeBackup(manifestFor(rows, eventCount = 2), rows)
        assertEquals(BackupFormatException.Reason.COUNT_MISMATCH, reasonOf { open(bytes, BackupLimits()).use { it.verifyAll(false) } })
    }

    @Test
    fun fewerRowsThanTheManifestSaysAreRejected() {
        val rows = testChain(3)
        val bytes = writeBackup(manifestFor(rows, eventCount = 4), rows)
        assertEquals(BackupFormatException.Reason.COUNT_MISMATCH, reasonOf { open(bytes, BackupLimits()).use { it.verifyAll(false) } })
    }

    @Test
    fun giantLineIsRejected() {
        val rows = testChain(1)
        val bytes = craftBackup(listOf(manifestLine(manifestFor(rows)), "x".repeat(10_000)))
        assertEquals(
            BackupFormatException.Reason.LINE_TOO_LONG,
            reasonOf { open(bytes, BackupLimits(maxLineBytes = 2_000)).use { it.verifyAll(false) } },
        )
    }

    @Test
    fun excessiveDecompressionIsRejected() {
        val rows = testChain(200)
        val bytes = writeBackup(manifestFor(rows), rows)
        assertEquals(
            BackupFormatException.Reason.DECOMPRESSED_TOO_LARGE,
            reasonOf { open(bytes, BackupLimits(maxDecompressedBytes = 5_000)).use { it.verifyAll(false) } },
        )
    }

    @Test
    fun invalidJsonInARowIsRejected() {
        val rows = testChain(1)
        val bytes = craftBackup(listOf(manifestLine(manifestFor(rows)), "{not json"))
        assertEquals(BackupFormatException.Reason.INVALID_JSON, reasonOf { open(bytes, BackupLimits()).use { it.verifyAll(false) } })
    }

    @Test
    fun invalidManifestIsRejected() {
        val bytes = craftBackup(listOf("this is not a manifest"))
        assertEquals(BackupFormatException.Reason.INVALID_JSON, reasonOf { open(bytes, BackupLimits()) })
    }
}
