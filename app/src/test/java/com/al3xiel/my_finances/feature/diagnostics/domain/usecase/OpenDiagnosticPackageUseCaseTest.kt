package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticPackageException.Reason
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageImporter
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageEventLine
import com.al3xiel.my_finances.feature.diagnostics.data.PackageFixtures
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceEventWriter
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenDiagnosticPackageUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x3C).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        override fun deleteKey() = Unit
    }

    private class RecordingWriter(var fail: Boolean = false, var cancel: Boolean = false) : WorkspaceEventWriter {
        val written = mutableListOf<PackageEventLine>()
        override suspend fun write(events: List<PackageEventLine>) {
            if (cancel) throw kotlin.coroutines.cancellation.CancellationException("cancelled")
            if (fail) throw java.io.IOException("disk full")
            written += events
        }
    }

    private val files = FakeDiagnosticFiles()
    private val writer = RecordingWriter()
    private lateinit var workspace: DiagnosticWorkspace
    private lateinit var wsFiles: WorkspaceFiles
    private lateinit var open: OpenDiagnosticPackageUseCase

    private fun setUp() {
        val directory = folder.newFolder("databases")
        wsFiles = WorkspaceFiles(directory)
        workspace = DiagnosticWorkspace(wsFiles, WorkspaceKeys(XorWrapper(), File(folder.root, "diagnostic.key"), SecureRandom())) {
            error("not opened")
        }
        open = OpenDiagnosticPackageUseCase(files, DiagnosticPackageImporter(), workspace, writer)
    }

    @Test
    fun opensAValidPackageIntoTheWorkspace() = runTest {
        setUp()
        val built = PackageFixtures.valid()
        files.files["doc://1"] = built.file

        val result = open("doc://1", null)

        val opened = result as OpenPackageResult.Opened
        assertEquals("x1", opened.exportId)
        assertEquals(3, opened.eventCount)
        assertEquals(listOf(1L, 2L, 3L), writer.written.map { it.sequence })
        assertEquals(built.packageHash, workspace.meta()?.packageHash)
        assertTrue(workspace.hasPackage())
    }

    @Test
    fun opensAnEncryptedPackageWithItsPassword() = runTest {
        setUp()
        files.files["doc://1"] = PackageFixtures.valid(PackageFixtures.password).file
        assertTrue(open("doc://1", PackageFixtures.password.copyOf()) is OpenPackageResult.Opened)
    }

    @Test
    fun aSecondPackageAsksToDeleteTheCurrentOneFirst() = runTest {
        setUp()
        files.files["doc://1"] = PackageFixtures.valid().file
        open("doc://1", null)
        writer.written.clear()

        assertEquals(OpenPackageResult.AlreadyOpen, open("doc://1", null))
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun cancellingThePickerDoesNothing() = runTest {
        setUp()
        assertEquals(OpenPackageResult.Cancelled, open(null, null))
        assertFalse(workspace.hasPackage())
    }

    @Test
    fun aRejectedPackageLeavesTheWorkspaceEmptyAndCarriesOnlyTheReason() = runTest {
        setUp()
        files.files["doc://1"] = ByteArray(100) { it.toByte() }

        assertEquals(OpenPackageResult.Rejected(Reason.NOT_A_PACKAGE), open("doc://1", null))
        assertFalse(workspace.hasPackage())
        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun anEncryptedPackageWithoutPasswordAsksForOneAndKeepsNothing() = runTest {
        setUp()
        files.files["doc://1"] = PackageFixtures.valid(PackageFixtures.password).file
        assertEquals(OpenPackageResult.Rejected(Reason.PASSWORD_REQUIRED), open("doc://1", null))
        assertFalse(workspace.hasPackage())
    }

    @Test
    fun aMissingDocumentIsAReadFailure() = runTest {
        setUp()
        assertEquals(OpenPackageResult.ReadFailed, open("doc://nope", null))
    }

    @Test
    fun anOrphanWorkspaceWithoutReadableMetadataIsDiscardedInsteadOfBlockingForever() = runTest {
        setUp()
        wsFiles.database.writeText("leftover")
        assertTrue(workspace.hasPackage())
        assertEquals(null, workspace.meta())
        files.files["doc://1"] = PackageFixtures.valid().file

        assertTrue(open("doc://1", null) is OpenPackageResult.Opened)
        assertEquals("x1", workspace.meta()?.manifest?.exportId)
    }

    @Test
    fun cancellingWhileWritingRethrowsAndStillLeavesNoWorkspaceBehind() = runTest {
        setUp()
        files.files["doc://1"] = PackageFixtures.valid().file
        writer.cancel = true
        val thrown = runCatching { open("doc://1", null) }.exceptionOrNull()
        assertTrue(thrown is kotlin.coroutines.cancellation.CancellationException)
        assertFalse(workspace.hasPackage())
    }

    @Test
    fun aFailureWhileWritingEventsLeavesNoWorkspaceBehind() = runTest {
        setUp()
        files.files["doc://1"] = PackageFixtures.valid().file
        writer.fail = true

        assertEquals(OpenPackageResult.ReadFailed, open("doc://1", null))
        assertFalse(workspace.hasPackage())
    }
}
