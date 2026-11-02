package com.al3xiel.my_finances.feature.backup.domain

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.backup.data.format.BackupFormatException
import com.al3xiel.my_finances.feature.backup.data.format.BackupLimits
import com.al3xiel.my_finances.feature.backup.data.format.BackupReader
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupResult
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class FakeBackupFilePort : BackupFilePort {
    val files = mutableMapOf<String, ByteArray>()
    var failWriteAfterBytes: Int? = null
    var corruptOnRead = false
    var deleteWorks = true
    var opened = 0
    val deleted = mutableListOf<String>()

    override suspend fun openOutput(uri: String): OutputStream {
        opened++
        val buffer = ByteArrayOutputStream()
        files[uri] = ByteArray(0)
        return object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

            override fun write(source: ByteArray, offset: Int, length: Int) {
                buffer.write(source, offset, length)
                val limit = failWriteAfterBytes
                if (limit != null && buffer.size() > limit) throw IOException("disk full")
            }

            override fun close() {
                files[uri] = buffer.toByteArray()
            }
        }
    }

    override suspend fun openInput(uri: String): InputStream? {
        val bytes = files[uri] ?: return null
        val copy = bytes.copyOf()
        if (corruptOnRead) copy[copy.size - 5] = (copy[copy.size - 5].toInt() xor 1).toByte()
        return ByteArrayInputStream(copy)
    }

    override suspend fun sizeOf(uri: String): Long? = files[uri]?.size?.toLong()

    override suspend fun delete(uri: String): Boolean {
        if (!deleteWorks) return false
        deleted += uri
        return files.remove(uri) != null
    }
}

class CreateBackupUseCaseTest {
    private val store = FakeEventStore()
    private val files = FakeBackupFilePort()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val ids = SequentialIdGenerator()
    private val password = "correct horse battery".toCharArray()
    private val day = LocalDate.of(2026, 10, 25)
    private val create = CreateBackupUseCase(store, files, EventRecorder(store, ids, clock), clock, ids, "test", 6, iterations = 1_000)

    private suspend fun seed(count: Int = 3) {
        store.append("c", (1..count).map { NewEvent(AccountRenamed(AccountId("a1"), "n$it"), day) })
    }

    private fun backupEvents() = store.events.filter { it.event.type == "BackupCreated" }

    @Test
    fun writesAVerifiedFileAndOnlyThenEmitsBackupCreated() = runTest {
        seed()
        val lastHash = store.events.last().hash
        val result = create("doc://1", password)

        assertEquals(CreateBackupResult.Created(eventCount = 3, chainIntact = true), result)
        val bytes = files.files.getValue("doc://1")
        BackupReader.open(ByteArrayInputStream(bytes), password, bytes.size.toLong(), BackupLimits()).use {
            assertEquals(3L, it.manifest.eventCount)
            assertEquals(lastHash, it.manifest.lastHash)
            assertEquals(3L, it.verifyAll(requireIntactChain = true))
        }
        val recorded = backupEvents().single().payload
        assertTrue(recorded.contains("\"eventCount\":3"))
        assertTrue(recorded.contains(lastHash))
    }

    @Test
    fun theBackupDoesNotContainItsOwnBackupCreatedEvent() = runTest {
        seed(2)
        create("doc://1", password)
        val bytes = files.files.getValue("doc://1")
        assertEquals(2L, BackupReader.open(ByteArrayInputStream(bytes), password, bytes.size.toLong(), BackupLimits()).use { it.manifest.eventCount })
        assertEquals(3, store.events.size)
    }

    @Test
    fun partialWriteDeletesTheDocumentAndEmitsNothing() = runTest {
        seed(50)
        files.failWriteAfterBytes = 40
        val result = create("doc://1", password)

        assertEquals(CreateBackupResult.WriteFailed(documentDeleted = true), result)
        assertNull(files.files["doc://1"])
        assertTrue(backupEvents().isEmpty())
    }

    @Test
    fun failedVerificationDeletesTheDocumentAndEmitsNothing() = runTest {
        seed()
        files.corruptOnRead = true
        val result = create("doc://1", password)

        assertEquals(CreateBackupResult.VerificationFailed(documentDeleted = true), result)
        assertNull(files.files["doc://1"])
        assertTrue(backupEvents().isEmpty())
    }

    @Test
    fun reportsWhenTheFailedDocumentCouldNotBeDeleted() = runTest {
        seed()
        files.corruptOnRead = true
        files.deleteWorks = false
        assertEquals(CreateBackupResult.VerificationFailed(documentDeleted = false), create("doc://1", password))
    }

    @Test
    fun brokenChainStillBacksUpWithTheFlagOff() = runTest {
        seed()
        store.events[1] = store.events[1].copy(payload = "{\"n\":\"tampered\"}")
        val result = create("doc://1", password)

        assertEquals(CreateBackupResult.Created(eventCount = 3, chainIntact = false), result)
        val bytes = files.files.getValue("doc://1")
        BackupReader.open(ByteArrayInputStream(bytes), password, bytes.size.toLong(), BackupLimits()).use {
            assertEquals(false, it.manifest.chainIntact)
        }
        assertEquals(1, backupEvents().size)
    }

    @Test
    fun cancellingTheSelectorDoesNothing() = runTest {
        seed()
        assertEquals(CreateBackupResult.Cancelled, create(null, password))
        assertEquals(0, files.opened)
        assertTrue(backupEvents().isEmpty())
    }

    @Test
    fun tamperedFilesAreNotAcceptedByTheReader() = runTest {
        seed()
        create("doc://1", password)
        val bytes = files.files.getValue("doc://1")
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        val failure = runCatching {
            BackupReader.open(ByteArrayInputStream(bytes), password, bytes.size.toLong(), BackupLimits()).use { it.verifyAll(true) }
        }.exceptionOrNull()
        assertTrue(failure is BackupFormatException)
    }
}
