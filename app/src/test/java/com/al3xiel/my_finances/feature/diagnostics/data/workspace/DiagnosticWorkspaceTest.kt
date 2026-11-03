package com.al3xiel.my_finances.feature.diagnostics.data.workspace

import com.al3xiel.my_finances.core.security.DatabaseKeyProvider
import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageManifest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.model.WorkspaceMeta
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** El espacio de diagnóstico guarda datos de un tercero (DG5): su borrado es completo y nunca toca la clave de la base real (D51). */
class DiagnosticWorkspaceTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class RecordingWrapper(private val mask: Byte) : KeyWrapper {
        var deleted = 0
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor mask.toInt()).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        override fun deleteKey() {
            deleted++
        }
    }

    private val realWrapper = RecordingWrapper(0x11)
    private val workspaceWrapper = RecordingWrapper(0x5A)
    private lateinit var realKeyFile: File
    private lateinit var directory: File
    private lateinit var files: WorkspaceFiles
    private lateinit var keys: WorkspaceKeys
    private lateinit var workspace: DiagnosticWorkspace

    private fun setUp() {
        realKeyFile = File(folder.root, "db.key")
        directory = folder.newFolder("databases")
        files = WorkspaceFiles(directory)
        keys = WorkspaceKeys(workspaceWrapper, File(folder.root, "diagnostic.key"), SecureRandom())
        workspace = DiagnosticWorkspace(files, keys) { error("the database is not opened in these tests") }
    }

    private fun meta() = WorkspaceMeta(
        manifest = PackageManifest(1, "export-1", "1.0", "14", "2026-11-02T12:00:00Z", "FULL", null, true, 7, 3, mapOf("ledger" to 1), emptyList(), "c0ffee"),
        projections = PackageProjections(mapOf("a1" to 1_000L), emptyList(), null, mapOf("ledger" to 1), null),
        packageHash = "abc123",
    )

    private fun touchAll() {
        listOf("diagnostic-workspace.db", "diagnostic-workspace.db-wal", "diagnostic-workspace.db-shm", "diagnostic-workspace.db-journal")
            .forEach { File(directory, it).writeText("x") }
    }

    @Test
    fun startsEmptyAndHoldsAPackageOnlyAfterSavingItsMetadata() = runTest {
        setUp()
        assertFalse(workspace.hasPackage())
        workspace.save(meta())
        assertTrue(workspace.hasPackage())
        assertEquals(meta(), workspace.meta())
    }

    @Test
    fun theMetadataIsSealedOnDisk() = runTest {
        setUp()
        workspace.save(meta())
        val onDisk = files.metaFile.readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(onDisk.contains("export-1"))
        assertFalse(onDisk.contains("abc123"))
    }

    @Test
    fun aSealedFileThatCannotBeOpenedReadsAsNoMetadata() = runTest {
        setUp()
        workspace.save(meta())
        files.metaFile.writeBytes(ByteArray(3))
        assertNull(workspace.meta())
        assertTrue("the workspace still counts as occupied so it can be deleted", workspace.hasPackage())
    }

    @Test
    fun deletingRemovesTheDatabaseItsSidecarsTheKeyAndTheSmallFiles() = runTest {
        setUp()
        keys.databaseKey()
        touchAll()
        workspace.save(meta())
        workspace.saveDictionary(byteArrayOf(1, 2, 3))

        workspace.delete()

        assertFalse(workspace.hasPackage())
        assertEquals(emptyList<String>(), directory.list()?.toList())
        assertFalse(File(folder.root, "diagnostic.key").exists())
        assertEquals(1, workspaceWrapper.deleted)
    }

    @Test
    fun deletingNeverTouchesTheKeyOfTheRealDatabase() = runTest {
        setUp()
        val real = DatabaseKeyProvider(realWrapper, realKeyFile, SecureRandom())
        val realKey = real.getOrCreate()
        keys.databaseKey()
        touchAll()

        workspace.delete()

        assertTrue(realKeyFile.exists())
        assertEquals(0, realWrapper.deleted)
        assertArrayEquals(realKey, real.getOrCreate())
    }

    @Test
    fun theWorkspaceKeyIsNotTheRealKeyAndIsStable() = runTest {
        setUp()
        val first = keys.databaseKey()
        assertArrayEquals(first, keys.databaseKey())
        assertEquals(32, first.size)
    }

    @Test
    fun deletingAnEmptyWorkspaceIsHarmless() = runTest {
        setUp()
        workspace.delete()
        workspace.delete()
        assertFalse(workspace.hasPackage())
    }

    @Test
    fun theDictionaryIsSealedAndGoneAfterDeleting() = runTest {
        setUp()
        val plain = "Account-A=Cuenta Secreta".toByteArray()
        workspace.saveDictionary(plain)
        assertFalse(files.dictionaryFile.readBytes().toString(Charsets.ISO_8859_1).contains("Secreta"))
        assertArrayEquals(plain, workspace.dictionary())
        workspace.delete()
        assertNull(workspace.dictionary())
    }

    @Test
    fun theWipeStepDeletesTheWorkspaceAndRunsWithTheKeys() = runTest {
        setUp()
        keys.databaseKey()
        touchAll()
        workspace.save(meta())
        val step = DiagnosticWorkspaceWipeStep(workspace)

        step.wipe()

        assertFalse(workspace.hasPackage())
        assertEquals(emptyList<String>(), directory.list()?.toList())
        assertEquals(WipeStep.KEY_PRIORITY, step.priority)
        assertNotNull(step.id)
    }
}
