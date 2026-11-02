package com.al3xiel.my_finances.feature.backup.data

import com.al3xiel.my_finances.domain.model.event.BackupRestored
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.EventCodec
import com.al3xiel.my_finances.domain.usecase.ChainVerifier
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.backup.data.format.BackupFormatException
import com.al3xiel.my_finances.feature.backup.data.format.BackupLimits
import com.al3xiel.my_finances.feature.backup.data.format.BackupReader
import com.al3xiel.my_finances.feature.backup.data.format.TEST_PASSWORD
import com.al3xiel.my_finances.feature.backup.data.format.manifestFor
import com.al3xiel.my_finances.feature.backup.data.format.testChain
import com.al3xiel.my_finances.feature.backup.data.format.writeBackup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private class FakeStagingSink(private val failOnInsert: Boolean = false) : StagingSink {
    val rows = mutableListOf<ChainRow>()
    var committed = false
    var discarded = false

    override suspend fun insert(rows: List<ChainRow>) {
        if (failOnInsert) throw IOException("disk full")
        this.rows += rows
    }

    override suspend fun commit() {
        committed = true
    }

    override suspend fun discard() {
        discarded = true
    }
}

class BackupImporterTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)

    private fun importer(sink: FakeStagingSink) =
        BackupImporter({ sink }, SequentialIdGenerator(), clock, "app-9")

    private fun reader(bytes: ByteArray) =
        BackupReader.open(ByteArrayInputStream(bytes), TEST_PASSWORD, bytes.size.toLong(), BackupLimits())

    private fun fileOf(rows: List<ChainRow>, manifest: com.al3xiel.my_finances.feature.backup.data.format.BackupManifest = manifestFor(rows)) =
        writeBackup(manifest, rows)

    @Test
    fun keepsEverySequenceAndAppendsARestoredEventChainedToTheLastHash() = runTest {
        val rows = testChain(1_200)
        val sink = FakeStagingSink()

        val result = importer(sink).import(reader(fileOf(rows)))

        assertEquals(1_200L, result.eventCount)
        assertEquals(1_201, sink.rows.size)
        assertEquals(rows.map { it.sequence }, sink.rows.take(1_200).map { it.sequence })
        val restored = sink.rows.last()
        assertEquals(1_201L, restored.sequence)
        assertEquals(rows.last().hash, restored.fields.previousHash)
        assertEquals("BackupRestored", restored.fields.type)
        assertEquals("BACKUP", restored.fields.aggregateType)
        assertEquals(BackupRestored("b1", 1_200), EventCodec.decode("BackupRestored", 1, restored.fields.payload))
        assertEquals(clock.millis(), restored.fields.recordedAt)
        assertEquals("app-9", restored.fields.appVersion)
        assertEquals(restored.hash, result.lastHash)
        val verifier = ChainVerifier()
        sink.rows.forEach { assertNull(verifier.accept(it)) }
        assertTrue(sink.committed)
        assertFalse(sink.discarded)
    }

    @Test
    fun anAlteredLinkDiscardsTheStagingDatabase() = runTest {
        val rows = testChain(10).toMutableList()
        val original = rows[4]
        rows[4] = original.copy(fields = original.fields.copy(payload = original.fields.payload.replace("n5", "n6")))
        val sink = FakeStagingSink()

        try {
            importer(sink).import(reader(fileOf(rows, manifestFor(testChain(10)))))
            fail("expected a broken chain")
        } catch (e: BackupFormatException) {
            assertEquals(BackupFormatException.Reason.CHAIN_BROKEN, e.reason)
        }

        assertTrue(sink.discarded)
        assertFalse(sink.committed)
    }

    @Test
    fun anUnknownEventTypeIsRejected() = runTest {
        val rows = testChain(3, type = "FromTheFuture")
        val sink = FakeStagingSink()

        try {
            importer(sink).import(reader(fileOf(rows)))
            fail("expected an unknown type")
        } catch (e: BackupFormatException) {
            assertEquals(BackupFormatException.Reason.UNKNOWN_EVENT_TYPE, e.reason)
        }

        assertTrue(sink.discarded)
    }

    @Test
    fun aLastHashThatDoesNotMatchTheManifestIsRejected() = runTest {
        val rows = testChain(3)
        val sink = FakeStagingSink()

        try {
            importer(sink).import(reader(fileOf(rows, manifestFor(rows, lastHash = "0".repeat(64)))))
            fail("expected a broken chain")
        } catch (e: BackupFormatException) {
            assertEquals(BackupFormatException.Reason.CHAIN_BROKEN, e.reason)
        }

        assertTrue(sink.discarded)
    }

    @Test
    fun aFileShorterThanItsManifestIsRejected() = runTest {
        val rows = testChain(5)
        val sink = FakeStagingSink()

        try {
            importer(sink).import(reader(fileOf(rows, manifestFor(rows, eventCount = 6))))
            fail("expected a count mismatch")
        } catch (e: BackupFormatException) {
            assertEquals(BackupFormatException.Reason.COUNT_MISMATCH, e.reason)
        }

        assertTrue(sink.discarded)
    }

    @Test
    fun aWriteFailureDiscardsTheStagingDatabaseAndPropagates() = runTest {
        val sink = FakeStagingSink(failOnInsert = true)

        try {
            importer(sink).import(reader(fileOf(testChain(3))))
            fail("expected an I/O failure")
        } catch (_: IOException) {
        }

        assertTrue(sink.discarded)
        assertFalse(sink.committed)
    }
}
