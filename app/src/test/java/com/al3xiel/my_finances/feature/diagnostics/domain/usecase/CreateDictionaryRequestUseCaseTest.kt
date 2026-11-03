package com.al3xiel.my_finances.feature.diagnostics.domain.usecase

import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.feature.diagnostics.data.PackageFixtures
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageImporter
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequestCodec
import com.al3xiel.my_finances.feature.diagnostics.domain.model.WorkspaceMeta
import java.io.ByteArrayInputStream
import java.io.File
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CreateDictionaryRequestUseCaseTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x55).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        override fun deleteKey() = Unit
    }

    private fun workspace() = DiagnosticWorkspace(WorkspaceFiles(folder.newFolder()), WorkspaceKeys(XorWrapper(), File(folder.root, "k"), SecureRandom())) { error("no") }

    @Test
    fun withoutAnOpenPackageThereIsNothingToRequest() {
        assertEquals(CreateRequestResult.NoPackage, CreateDictionaryRequestUseCase(workspace())(listOf("Account-A"), emptyList()))
    }

    @Test
    fun theCodeNamesThePackageAndCarriesTheChosenPseudonymsSortedAndWithoutRepeats() {
        val ws = workspace()
        val built = PackageFixtures.valid()
        val parsed = DiagnosticPackageImporter().read(ByteArrayInputStream(built.file), built.file.size.toLong(), null)
        ws.save(WorkspaceMeta(parsed.manifest, parsed.projections, parsed.packageHash))

        val code = (CreateDictionaryRequestUseCase(ws)(listOf("Person-1", "Account-A", "Person-1"), listOf("e2", "e1")) as CreateRequestResult.Ready).code
        val request = DictionaryRequestCodec.decode(code)

        assertEquals("x1", request?.exportId)
        assertEquals(built.packageHash.take(12), request?.packagePrefix)
        assertEquals(listOf("Account-A", "Person-1"), request?.pseudonyms)
        assertEquals(listOf("e1", "e2"), request?.eventIds)
    }
}
