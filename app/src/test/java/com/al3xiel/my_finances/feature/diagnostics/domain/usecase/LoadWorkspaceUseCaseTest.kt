package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.feature.diagnostics.data.PackageFixtures
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.domain.model.WorkspaceMeta
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LoadWorkspaceUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x44).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        override fun deleteKey() = Unit
    }

    private lateinit var wsFiles: WorkspaceFiles
    private lateinit var workspace: DiagnosticWorkspace

    private fun setUp() {
        wsFiles = WorkspaceFiles(folder.newFolder())
        workspace = DiagnosticWorkspace(wsFiles, WorkspaceKeys(XorWrapper(), File(folder.root, "k"), SecureRandom())) { error("no") }
    }

    @Test
    fun describesTheOpenPackageWithoutNames() = runTest {
        setUp()
        val parsed = com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageImporter().let {
            val built = PackageFixtures.valid()
            it.read(java.io.ByteArrayInputStream(built.file), built.file.size.toLong(), null)
        }
        workspace.save(WorkspaceMeta(parsed.manifest, parsed.projections, parsed.packageHash))

        val summary = LoadWorkspaceUseCase(workspace) { listOf("Account-A") }()

        assertEquals("x1", summary?.exportId)
        assertEquals(3, summary?.eventCount)
        assertEquals(listOf("Account-A"), summary?.pseudonyms)
        assertEquals(12, summary?.packageHashPrefix?.length)
    }

    @Test
    fun nothingOpenGivesNoSummary() = runTest {
        setUp()
        assertNull(LoadWorkspaceUseCase(workspace) { emptyList() }())
    }

    @Test
    fun anOrphanWorkspaceIsDiscardedSoItCanNeverStayUndeletable() = runTest {
        setUp()
        wsFiles.database.writeText("leftover")
        assertNull(LoadWorkspaceUseCase(workspace) { emptyList() }())
        assertFalse(workspace.hasPackage())
    }
}
