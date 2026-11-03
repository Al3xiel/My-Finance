package com.al3xiel.my_finances.feature.backup.domain.format

import com.al3xiel.my_finances.domain.model.event.ChainRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupFormatTest {
    private fun open(bytes: ByteArray, password: CharArray = TEST_PASSWORD, limits: BackupLimits = BackupLimits()) =
        BackupReader.open(ByteArrayInputStream(bytes), password, bytes.size.toLong(), limits)

    private fun reasonOf(block: () -> Unit) = assertThrows(BackupFormatException::class.java, block).reason

    @Test
    fun roundTripKeepsManifestAndEveryRowInBatches() {
        val rows = testChain(5)
        val manifest = manifestFor(rows)
        open(writeBackup(manifest, rows)).use { reader ->
            assertEquals(manifest, reader.manifest)
            val read = mutableListOf<ChainRow>()
            while (true) {
                val batch = reader.nextBatch(2)
                if (batch.isEmpty()) break
                assertEquals(true, batch.size <= 2)
                read += batch
            }
            assertEquals(rows, read)
        }
    }

    @Test
    fun emptyBackupRoundTrips() {
        val manifest = manifestFor(emptyList())
        open(writeBackup(manifest, emptyList())).use {
            assertEquals(manifest, it.manifest)
            assertEquals(emptyList<ChainRow>(), it.nextBatch(10))
        }
    }

    @Test
    fun summaryNeedsOnlyTheManifest() {
        val rows = testChain(3)
        val manifest = open(writeBackup(manifestFor(rows), rows)).use { it.manifest }
        assertEquals(3L, manifest.eventCount)
        assertEquals(rows.last().hash, manifest.lastHash)
    }

    @Test
    fun verifyAllAcceptsAnIntactBackup() {
        val rows = testChain(4)
        open(writeBackup(manifestFor(rows), rows)).use { assertEquals(4L, it.verifyAll(requireIntactChain = true)) }
    }

    @Test
    fun wrongPasswordIsRejected() {
        val rows = testChain(2)
        val bytes = writeBackup(manifestFor(rows), rows)
        assertEquals(
            BackupFormatException.Reason.WRONG_PASSWORD_OR_TAMPERED,
            reasonOf { open(bytes, "another long password".toCharArray()) },
        )
    }

    @Test
    fun aFileThatIsNotABackupIsRejected() {
        assertEquals(BackupFormatException.Reason.NOT_A_BACKUP, reasonOf { open(ByteArray(200) { 7 }) })
    }

    @Test
    fun brokenChainIsRejectedWhenRequired() {
        val rows = testChain(3).toMutableList()
        rows[1] = rows[1].copy(fields = rows[1].fields.copy(payload = "{}"))
        val bytes = writeBackup(manifestFor(rows, chainIntact = false), rows)
        assertEquals(BackupFormatException.Reason.CHAIN_BROKEN, reasonOf { open(bytes).use { it.verifyAll(requireIntactChain = true) } })
        open(bytes).use { assertEquals(3L, it.verifyAll(requireIntactChain = false)) }
    }

    @Test
    fun lastHashOfTheManifestMustMatchTheChain() {
        val rows = testChain(3)
        val bytes = writeBackup(manifestFor(rows, lastHash = "forged"), rows)
        assertEquals(BackupFormatException.Reason.CHAIN_BROKEN, reasonOf { open(bytes).use { it.verifyAll(requireIntactChain = true) } })
    }

    @Test
    fun unknownEventTypeIsRejected() {
        val rows = testChain(2, type = "SomethingNew", payload = { "{}" })
        val bytes = writeBackup(manifestFor(rows), rows)
        assertEquals(BackupFormatException.Reason.UNKNOWN_EVENT_TYPE, reasonOf { open(bytes).use { it.verifyAll(true) } })
    }

    @Test
    fun newerEventSchemaIsRejectedWithTheVersionMessage() {
        val rows = testChain(2)
        val bytes = writeBackup(manifestFor(rows, maxEventSchemaVersion = 99), rows)
        assertEquals(BackupFormatException.Reason.NEWER_SCHEMA, reasonOf { open(bytes) })
    }

    @Test
    fun aTamperedByteIsDetected() {
        val rows = testChain(3)
        val bytes = writeBackup(manifestFor(rows), rows)
        bytes[bytes.size - 20] = (bytes[bytes.size - 20].toInt() xor 1).toByte()
        val reason = reasonOf { open(bytes).use { it.verifyAll(true) } }
        assertEquals(
            true,
            reason == BackupFormatException.Reason.TAMPERED_OR_TRUNCATED || reason == BackupFormatException.Reason.WRONG_PASSWORD_OR_TAMPERED,
        )
    }
}
