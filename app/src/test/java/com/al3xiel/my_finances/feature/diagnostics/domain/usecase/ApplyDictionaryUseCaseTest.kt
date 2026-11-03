package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.feature.diagnostics.data.DictionaryFile
import com.al3xiel.my_finances.feature.diagnostics.data.DictionaryFormat
import com.al3xiel.my_finances.feature.diagnostics.data.PackageManifest
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceMeta
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApplyDictionaryUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x17).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        override fun deleteKey() = Unit
    }

    private val files = FakeDiagnosticFiles()
    private val password = "correct horse battery".toCharArray()
    private lateinit var workspace: DiagnosticWorkspace
    private lateinit var apply: ApplyDictionaryUseCase

    private fun setUp(withPackage: Boolean = true) {
        workspace = DiagnosticWorkspace(WorkspaceFiles(folder.newFolder()), WorkspaceKeys(XorWrapper(), File(folder.root, "k"), SecureRandom())) { error("no") }
        if (withPackage) {
            workspace.save(
                WorkspaceMeta(
                    PackageManifest(1, "x1", "1.0", "14", "2026-11-02T00:00:00Z", "RECONCILIATION", "r1", true, 5, 3, emptyMap(), emptyList(), "c"),
                    PackageProjections(emptyMap(), emptyList(), null, emptyMap(), null), "hash-1",
                ),
            )
        }
        apply = ApplyDictionaryUseCase(files, workspace)
    }

    private fun dictionary(exportId: String = "x1", packageHash: String = "hash-1") =
        DictionaryFile(1, "d1", exportId, packageHash, DictionaryScope.PARTIAL, mapOf("Account-A" to "Cuenta Secreta"), mapOf("e4" to listOf("nota")))

    private fun put(uri: String, file: DictionaryFile, secret: CharArray = password) {
        files.files[uri] = DictionaryFormat.write(file, secret.copyOf(), iterations = 1_000)
    }

    @Test
    fun appliesAMatchingDictionaryAndKeepsItSealedInsideTheWorkspace() = runTest {
        setUp()
        put("doc://k", dictionary())

        assertEquals(ApplyDictionaryResult.Applied(revealedPseudonyms = 1, revealedNotes = 1), apply("doc://k", password.copyOf()))

        assertEquals("Cuenta Secreta", apply.applied()?.aliases?.get("Account-A"))
    }

    @Test
    fun aDictionaryOfAnotherPackageHashIsRefused() = runTest {
        setUp()
        put("doc://k", dictionary(packageHash = "other-hash"))
        assertEquals(ApplyDictionaryResult.Mismatch, apply("doc://k", password.copyOf()))
        assertNull(apply.applied())
    }

    @Test
    fun aDictionaryOfAnotherExportIsRefused() = runTest {
        setUp()
        put("doc://k", dictionary(exportId = "x2"))
        assertEquals(ApplyDictionaryResult.Mismatch, apply("doc://k", password.copyOf()))
    }

    @Test
    fun aWrongPasswordIsReportedAndAppliesNothing() = runTest {
        setUp()
        put("doc://k", dictionary())
        assertEquals(ApplyDictionaryResult.WrongPassword, apply("doc://k", "another password!".toCharArray()))
        assertNull(apply.applied())
    }

    @Test
    fun aFileThatIsNotADictionaryIsRefused() = runTest {
        setUp()
        files.files["doc://k"] = ByteArray(100) { it.toByte() }
        assertEquals(ApplyDictionaryResult.NotADictionary, apply("doc://k", password.copyOf()))
    }

    @Test
    fun withoutAnOpenPackageThereIsNothingToApplyTo() = runTest {
        setUp(withPackage = false)
        put("doc://k", dictionary())
        assertEquals(ApplyDictionaryResult.NoPackage, apply("doc://k", password.copyOf()))
    }

    @Test
    fun cancellingThePickerDoesNothing() = runTest {
        setUp()
        assertEquals(ApplyDictionaryResult.Cancelled, apply(null, password.copyOf()))
    }

    @Test
    fun theFormatRoundTripsAndKeepsTheCallersPassword() {
        val mine = password.copyOf()
        val bytes = DictionaryFormat.write(dictionary(), password.copyOf(), iterations = 1_000)
        assertEquals(dictionary(), DictionaryFormat.read(bytes, mine))
        assertArrayEquals(password, mine)
    }
}
