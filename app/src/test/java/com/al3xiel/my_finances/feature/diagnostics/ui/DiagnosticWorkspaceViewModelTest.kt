package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.ExternalFlowGuard
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.core.security.KeyWrapper
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageImporter
import com.al3xiel.my_finances.feature.diagnostics.data.DictionaryFile
import com.al3xiel.my_finances.feature.diagnostics.data.DictionaryFormat
import com.al3xiel.my_finances.feature.diagnostics.data.PackageEventLine
import com.al3xiel.my_finances.feature.diagnostics.data.PackageFixtures
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.DiagnosticWorkspace
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceEventWriter
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceFiles
import com.al3xiel.my_finances.feature.diagnostics.data.workspace.WorkspaceKeys
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequestCodec
import com.al3xiel.my_finances.feature.diagnostics.domain.model.PackageProjections
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.WorkspaceReplayer
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ApplyDictionaryUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.BuildDivergenceReportUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.CreateDictionaryRequestUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.DeleteDiagnosticWorkspaceUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.FakeDiagnosticFiles
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.LoadWorkspaceUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.OpenDiagnosticPackageUseCase
import com.al3xiel.my_finances.feature.privacy.FakeSecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import java.io.File
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticWorkspaceViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class XorWrapper : KeyWrapper {
        override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x2B).toByte() }
        override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
        override fun deleteKey() = Unit
    }

    private val dispatcher = UnconfinedTestDispatcher()
    private val prompt = AuthPrompt("Confirma")
    private val files = FakeDiagnosticFiles()
    private val security = FakeSecuritySettingsRepository(SecuritySettings(lockEnabled = false))
    private val authenticator = FakeAuthenticator()
    private val guard = ExternalFlowGuard(object : MonotonicClock { override fun nowMillis() = 0L })
    private val password = "correct horse battery".toCharArray()
    private lateinit var workspace: DiagnosticWorkspace

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): DiagnosticWorkspaceViewModel {
        workspace = DiagnosticWorkspace(
            WorkspaceFiles(folder.newFolder()), WorkspaceKeys(XorWrapper(), File(folder.root, "k"), SecureRandom()),
        ) { error("not opened") }
        val writer = WorkspaceEventWriter { _: List<PackageEventLine> -> }
        val replayer = object : WorkspaceReplayer {
            override suspend fun replay(accountId: AccountId?, reconciliationId: ReconciliationId?, includeOrigin: Boolean): PackageProjections =
                PackageFixtures.input().projections
        }
        return DiagnosticWorkspaceViewModel(
            load = LoadWorkspaceUseCase(workspace) { listOf("Account-A", "Person-1") },
            open = OpenDiagnosticPackageUseCase(files, DiagnosticPackageImporter(), workspace, writer),
            buildReport = BuildDivergenceReportUseCase(workspace, replayer),
            createRequest = CreateDictionaryRequestUseCase(workspace),
            apply = ApplyDictionaryUseCase(files, workspace),
            delete = DeleteDiagnosticWorkspaceUseCase(workspace),
            securitySettings = security,
            authenticator = authenticator,
            externalFlow = guard,
            ioDispatcher = dispatcher,
        )
    }

    private fun DiagnosticWorkspaceViewModel.enter() = onAction(DiagnosticWorkspaceUiAction.Authenticate(prompt))

    private fun DiagnosticWorkspaceViewModel.pick(uri: String) {
        onAction(DiagnosticWorkspaceUiAction.OpenPackage)
        onAction(DiagnosticWorkspaceUiAction.PickerOpened)
        onAction(DiagnosticWorkspaceUiAction.PackagePicked(uri))
    }

    private fun dictionaryBytes(exportId: String = "x1", hash: String? = null) = DictionaryFormat.write(
        DictionaryFile(1, "d1", exportId, hash ?: PackageFixtures.valid().packageHash, DictionaryScope.PARTIAL, mapOf("Account-A" to "Cuenta Secreta"), emptyMap()),
        password.copyOf(), iterations = 1_000,
    )

    @Test
    fun nothingIsShownUntilTheCredentialIsConfirmed() {
        security.state.value = SecuritySettings(lockEnabled = true)
        authenticator.result = AuthResult.Failed
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid().file

        vm.enter()
        assertEquals(WorkspaceStage.LOCKED, vm.uiState.value.stage)
        assertEquals(WorkspaceMessage.AUTH_FAILED, vm.uiState.value.message)

        vm.pick("doc://1")
        assertEquals(WorkspaceStage.LOCKED, vm.uiState.value.stage)
        assertNull(vm.uiState.value.summary)
        assertFalse(workspace.hasPackage())
    }

    @Test
    fun confirmingTheCredentialShowsAnEmptyWorkspace() {
        security.state.value = SecuritySettings(lockEnabled = true)
        val vm = viewModel()
        vm.enter()
        assertEquals(1, authenticator.calls)
        assertEquals(WorkspaceStage.EMPTY, vm.uiState.value.stage)
    }

    @Test
    fun openingAPackageShowsItsSummaryAndTheReport() {
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid().file
        vm.enter()
        vm.pick("doc://1")

        val state = vm.uiState.value
        assertEquals(WorkspaceStage.READY, state.stage)
        assertEquals(3, state.summary?.eventCount)
        assertEquals("x1", state.summary?.exportId)
        assertEquals(false, state.report?.diverges)
        assertFalse(guard.isActive())
    }

    @Test
    fun anEncryptedPackageAsksForItsPasswordAndAllowsRetrying() {
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid(PackageFixtures.password).file
        vm.enter()
        vm.pick("doc://1")
        assertEquals(PasswordTarget.PACKAGE, vm.uiState.value.askPassword)

        val wrong = "wrong password!!".toCharArray()
        vm.onAction(DiagnosticWorkspaceUiAction.SubmitPassword(wrong))
        assertEquals(PasswordTarget.PACKAGE, vm.uiState.value.askPassword)
        assertEquals(WorkspaceMessage.WRONG_PASSWORD, vm.uiState.value.message)
        assertTrue(wrong.all { it == '\u0000' })

        val right = PackageFixtures.password.copyOf()
        vm.onAction(DiagnosticWorkspaceUiAction.SubmitPassword(right))
        assertEquals(WorkspaceStage.READY, vm.uiState.value.stage)
        assertNull(vm.uiState.value.askPassword)
        assertTrue(right.all { it == '\u0000' })
    }

    @Test
    fun aRejectedPackageShowsOnlyAGenericMessage() {
        val vm = viewModel()
        files.files["doc://1"] = ByteArray(100) { it.toByte() }
        vm.enter()
        vm.pick("doc://1")
        assertEquals(WorkspaceStage.EMPTY, vm.uiState.value.stage)
        assertEquals(WorkspaceMessage.NOT_A_PACKAGE, vm.uiState.value.message)
    }

    @Test
    fun aSecondPackageAsksToDeleteTheCurrentOne() {
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid().file
        vm.enter()
        vm.pick("doc://1")
        vm.pick("doc://1")
        assertEquals(WorkspaceMessage.ALREADY_OPEN, vm.uiState.value.message)
        assertEquals(WorkspaceStage.READY, vm.uiState.value.stage)
    }

    @Test
    fun theRequestCarriesOnlyTheChosenPseudonyms() {
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid().file
        vm.enter()
        vm.pick("doc://1")
        vm.onAction(DiagnosticWorkspaceUiAction.ToggleRequestPseudonym("Account-A"))
        vm.onAction(DiagnosticWorkspaceUiAction.CreateRequest)

        val request = DictionaryRequestCodec.decode(vm.uiState.value.requestCode.orEmpty())
        assertNotNull(request)
        assertEquals(listOf("Account-A"), request?.pseudonyms)
        assertEquals("x1", request?.exportId)
        vm.onAction(DiagnosticWorkspaceUiAction.DismissRequest)
        assertNull(vm.uiState.value.requestCode)
    }

    @Test
    fun aMatchingDictionaryRevealsNamesOnlyInsideTheWorkspace() {
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid().file
        files.files["doc://k"] = dictionaryBytes()
        vm.enter()
        vm.pick("doc://1")
        vm.onAction(DiagnosticWorkspaceUiAction.ApplyDictionary)
        vm.onAction(DiagnosticWorkspaceUiAction.PickerOpened)
        vm.onAction(DiagnosticWorkspaceUiAction.DictionaryPicked("doc://k"))
        assertEquals(PasswordTarget.DICTIONARY, vm.uiState.value.askPassword)
        vm.onAction(DiagnosticWorkspaceUiAction.SubmitPassword(password.copyOf()))

        assertEquals("Cuenta Secreta", vm.uiState.value.names["Account-A"])
        assertEquals(WorkspaceStage.READY, vm.uiState.value.stage)
    }

    @Test
    fun aDictionaryOfAnotherPackageIsRefused() {
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid().file
        files.files["doc://k"] = dictionaryBytes(hash = "someone-else")
        vm.enter()
        vm.pick("doc://1")
        vm.onAction(DiagnosticWorkspaceUiAction.ApplyDictionary)
        vm.onAction(DiagnosticWorkspaceUiAction.PickerOpened)
        vm.onAction(DiagnosticWorkspaceUiAction.DictionaryPicked("doc://k"))
        vm.onAction(DiagnosticWorkspaceUiAction.SubmitPassword(password.copyOf()))

        assertEquals(WorkspaceMessage.DICTIONARY_MISMATCH, vm.uiState.value.message)
        assertTrue(vm.uiState.value.names.isEmpty())
    }

    @Test
    fun deletingAsksForConfirmationAndLeavesNothingBehind() {
        val vm = viewModel()
        files.files["doc://1"] = PackageFixtures.valid().file
        vm.enter()
        vm.pick("doc://1")
        vm.onAction(DiagnosticWorkspaceUiAction.AskDelete)
        assertTrue(vm.uiState.value.askDelete)
        assertTrue(workspace.hasPackage())

        vm.onAction(DiagnosticWorkspaceUiAction.ConfirmDelete)

        assertEquals(WorkspaceStage.EMPTY, vm.uiState.value.stage)
        assertNull(vm.uiState.value.summary)
        assertFalse(workspace.hasPackage())
    }
}
