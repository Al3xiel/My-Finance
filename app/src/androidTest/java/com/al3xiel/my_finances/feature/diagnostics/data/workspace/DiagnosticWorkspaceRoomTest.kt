package com.al3xiel.my_finances.feature.diagnostics.data.workspace

import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageManifest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.WorkspaceMeta

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.al3xiel.my_finances.core.di.DATABASE_NAME
import com.al3xiel.my_finances.core.security.AndroidKeystoreKeyWrapper
import com.al3xiel.my_finances.data.database.AppDatabase
import com.al3xiel.my_finances.data.eventstore.EventTriggersCallback
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageEventLine
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** La segunda base de verdad: cifrada con su propia clave, con los triggers append-only y que se borra sin dejar nada. */
@RunWith(AndroidJUnit4::class)
class DiagnosticWorkspaceRoomTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val directory = requireNotNull(context.getDatabasePath("diagnostic-workspace.db").absoluteFile.parentFile)
    private val keyFile = File(context.noBackupFilesDir, "diagnostic-test.key")
    private lateinit var workspace: DiagnosticWorkspace

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val keys = WorkspaceKeys(AndroidKeystoreKeyWrapper("diagnostic_workspace_test_wrapper"), keyFile, SecureRandom())
        workspace = DiagnosticWorkspace(WorkspaceFiles(directory), keys) { key ->
            Room.databaseBuilder(context, AppDatabase::class.java, "diagnostic-workspace.db")
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addCallback(EventTriggersCallback)
                .build()
        }
        workspace.delete()
    }

    @After
    fun tearDown() = workspace.delete()

    private fun line(sequence: Long) = PackageEventLine(
        sequence = sequence, eventId = "e$sequence", type = "PersonCreated", schemaVersion = 1, aggregateType = "PERSON",
        aggregateId = "p$sequence", correlationId = "c", payload = "{\"personId\":\"p$sequence\",\"name\":\"Person-$sequence\"}",
        occurredOn = "2026-10-25", recordedAt = 1_000L + sequence, appVersion = "1.0", previousHash = "h${sequence - 1}", hash = "h$sequence",
    )

    @Test
    fun insertsEventsKeepingTheirSequenceAndHashes() = runBlocking {
        WorkspaceEventSink(workspace).write(listOf(line(5), line(6), line(9)))

        val stored = workspace.database().eventDao().all()
        assertEquals(listOf(5L, 6L, 9L), stored.map { it.sequence })
        assertEquals(listOf("h5", "h6", "h9"), stored.map { it.hash })
        assertEquals(listOf("h4", "h5", "h8"), stored.map { it.previousHash })
    }

    @Test
    fun theWorkspaceKeepsTheAppendOnlyTriggers() = runBlocking {
        WorkspaceEventSink(workspace).write(listOf(line(1)))
        val failure = runCatching { workspace.database().openHelper.writableDatabase.execSQL("UPDATE events SET type = 'X'") }
        assertTrue(failure.isFailure)
    }

    @Test
    fun theDatabaseFileIsEncrypted() = runBlocking {
        WorkspaceEventSink(workspace).write(listOf(line(1)))
        workspace.database().close()
        val header = File(directory, "diagnostic-workspace.db").inputStream().use { it.readNBytes(15) }.toString(Charsets.ISO_8859_1)
        assertFalse(header.startsWith("SQLite format 3"))
    }

    @Test
    fun deletingLeavesNothingAndNeverTouchesTheRealDatabaseFile() = runBlocking {
        val real = context.getDatabasePath(DATABASE_NAME)
        val realBefore = real.takeIf { it.exists() }?.lastModified()
        WorkspaceEventSink(workspace).write(listOf(line(1)))
        workspace.save(WorkspaceMeta(sampleManifest(), sampleProjections(), "hash"))
        assertTrue(workspace.hasPackage())

        workspace.delete()

        assertFalse(workspace.hasPackage())
        assertEquals(emptyList<String>(), directory.list().orEmpty().filter { it.startsWith("diagnostic-workspace") })
        assertFalse(keyFile.exists())
        assertEquals(realBefore, real.takeIf { it.exists() }?.lastModified())
    }

    private fun sampleManifest() = com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageManifest(
        1, "x", "1.0", "14", "2026-11-02T00:00:00Z", "FULL", null, true, 1, 1, emptyMap(), emptyList(), "c",
    )

    private fun sampleProjections() =
        com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections(emptyMap(), emptyList(), null, emptyMap(), null)
}
