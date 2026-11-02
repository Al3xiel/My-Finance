package com.al3xiel.my_finances.feature.backup.domain

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.SystemCatalog
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.ProjectionsRebuilt
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.FakeRestoreMarker
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.backup.data.BackupImporter
import com.al3xiel.my_finances.feature.backup.data.StagingSink
import com.al3xiel.my_finances.feature.backup.data.format.BackupFormatException.Reason
import com.al3xiel.my_finances.feature.backup.data.format.TEST_PASSWORD
import com.al3xiel.my_finances.feature.backup.data.format.manifestFor
import com.al3xiel.my_finances.feature.backup.data.format.testChain
import com.al3xiel.my_finances.feature.backup.data.format.writeBackup
import com.al3xiel.my_finances.feature.backup.domain.repository.AppRestarter
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.backup.domain.repository.DatabaseSwapper
import com.al3xiel.my_finances.feature.backup.domain.usecase.CanRestoreUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestorePreview
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreBackupUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class FakeRestoreFiles(var bytes: ByteArray?) : BackupFilePort {
    var opened = 0
    override suspend fun openOutput(uri: String): OutputStream? = null
    override suspend fun openInput(uri: String): InputStream? = bytes?.let { opened++; ByteArrayInputStream(it) }
    override suspend fun sizeOf(uri: String): Long? = bytes?.size?.toLong()
    override suspend fun delete(uri: String) = false
}

private class RecordingSink(val log: MutableList<String>) : StagingSink {
    override suspend fun insert(rows: List<ChainRow>) { log += "insert" }
    override suspend fun commit() { log += "commit" }
    override suspend fun discard() { log += "discard" }
}

class RestoreBackupUseCaseTest {
    private val log = mutableListOf<String>()
    private val store = FakeEventStore()
    private val marker = FakeRestoreMarker()
    private val rows = testChain(4)
    private val files = FakeRestoreFiles(writeBackup(manifestFor(rows), rows))
    private var swapFails = false
    private val swapper = DatabaseSwapper {
        log += "swap"
        if (swapFails) throw IOException("rename failed")
    }
    private val restarter = AppRestarter { log += "restart" }
    private val clock = Clock.fixed(Instant.parse("2026-11-02T15:00:00Z"), ZoneOffset.UTC)
    private val restore = RestoreBackupUseCase(
        files,
        CanRestoreUseCase(store),
        BackupImporter({ RecordingSink(log) }, SequentialIdGenerator(), clock, "t"),
        swapper,
        marker,
        restarter,
    )

    private suspend fun userData() {
        store.append("c", listOf(NewEvent(AccountRenamed(AccountId("a"), "x"), LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun restoresInOrderMarkingBeforeTheSwapAndRestartingLast() = runTest {
        val result = restore("content://doc", TEST_PASSWORD)

        assertEquals(RestoreResult.Restored(4), result)
        assertEquals(listOf("insert", "insert", "commit", "swap", "restart"), log)
        assertTrue(marker.pending)
    }

    @Test
    fun aWrongPasswordTouchesNothing() = runTest {
        val result = restore("content://doc", "not the password".toCharArray())

        assertEquals(RestoreResult.Rejected(Reason.WRONG_PASSWORD_OR_TAMPERED), result)
        assertTrue(log.isEmpty())
        assertFalse(marker.pending)
    }

    @Test
    fun anAlteredFileIsRejectedAndNeverSwapped() = runTest {
        val bytes = files.bytes!!.copyOf()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        files.bytes = bytes

        val result = restore("content://doc", TEST_PASSWORD)

        assertTrue(result is RestoreResult.Rejected)
        assertFalse(log.contains("swap"))
        assertFalse(marker.pending)
    }

    @Test
    fun aNewerSchemaIsRejectedBeforeImporting() = runTest {
        files.bytes = writeBackup(manifestFor(rows, maxEventSchemaVersion = 99), rows)

        assertEquals(RestoreResult.Rejected(Reason.NEWER_SCHEMA), restore("content://doc", TEST_PASSWORD))
        assertTrue(log.isEmpty())
    }

    @Test
    fun aDatabaseWithUserDataIsNeverRestoredOver() = runTest {
        userData()

        assertEquals(RestoreResult.NotAllowed, restore("content://doc", TEST_PASSWORD))
        assertEquals(0, files.opened)
        assertTrue(log.isEmpty())
    }

    @Test
    fun aFailedSwapClearsTheMarkerAndDoesNotRestart() = runTest {
        swapFails = true

        assertEquals(RestoreResult.Failed, restore("content://doc", TEST_PASSWORD))

        assertFalse(marker.pending)
        assertFalse(log.contains("restart"))
    }

    @Test
    fun aMissingDocumentIsUnreadableAndACancelledPickerDoesNothing() = runTest {
        files.bytes = null
        assertEquals(RestoreResult.Unreadable, restore("content://doc", TEST_PASSWORD))
        assertEquals(RestoreResult.Cancelled, restore(null, TEST_PASSWORD))
        assertTrue(log.isEmpty())
    }

    @Test
    fun previewShowsCountsAndDatesWithoutImporting() = runTest {
        val preview = restore.preview("content://doc", TEST_PASSWORD)

        assertTrue(preview is RestorePreview.Ready)
        preview as RestorePreview.Ready
        assertEquals(4L, preview.summary.eventCount)
        assertTrue(preview.summary.chainIntact)
        assertTrue(log.isEmpty())
    }

    @Test
    fun previewIsRefusedWhenTheDatabaseHasData() = runTest {
        userData()

        assertEquals(RestorePreview.NotAllowed, restore.preview("content://doc", TEST_PASSWORD))
    }

    @Test
    fun seedEventsAloneCountAsAnEmptyDatabase() = runTest {
        val seed = SystemCatalog.incomeSources.map { NewEvent(IncomeSourceCreated(it.id, it.name, null), LocalDate.of(2026, 11, 1)) } +
            SystemCatalog.defaultCategories.map { NewEvent(CategoryCreated(it.id, it.name, it.icon, it.color, null), LocalDate.of(2026, 11, 1)) } +
            NewEvent(ProjectionsRebuilt(emptyMap(), "VERSION_CHANGE"), LocalDate.of(2026, 11, 1))
        store.append("seed", seed)
        assertTrue(CanRestoreUseCase(store)())

        store.append("c", listOf(NewEvent(CategoryCreated(CategoryId("mine"), "Mine", "i", "#000", null), LocalDate.of(2026, 11, 1))))
        assertFalse(CanRestoreUseCase(store)())
        store.append("d", listOf(NewEvent(IncomeSourceCreated(IncomeSourceId("other"), "Job", null), LocalDate.of(2026, 11, 1))))
        assertFalse(CanRestoreUseCase(store)())
    }
}
