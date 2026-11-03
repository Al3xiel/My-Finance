package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.ExternalFlowGuard
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageBuilder
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticSummaryRenderer
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.FakeDiagnosticFiles
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.FakeSnapshots
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.SelectDiagnosticEventsUseCase
import com.al3xiel.my_finances.feature.privacy.FakeSecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
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
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticExportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val prompt = AuthPrompt("Confirma")
    private val store = FakeEventStore()
    private val files = FakeDiagnosticFiles()
    private val security = FakeSecuritySettingsRepository(SecuritySettings(lockEnabled = false))
    private val authenticator = FakeAuthenticator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val ids = SequentialIdGenerator()
    private val guard = ExternalFlowGuard(object : MonotonicClock { override fun nowMillis() = 0L })

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun seed() = runBlocking {
        val day = LocalDate.of(2026, 10, 25)
        store.append(
            "c",
            listOf(
                PersonCreated(PersonId("p1"), "Ana"),
                AccountCreated(AccountId("a1"), "Cuenta", AccountType.BANK, CurrencyCode.PEN, null, Money(0, CurrencyCode.PEN)),
                BalanceCheckRecorded(ReconciliationId("r1"), AccountId("a1"), Money(9_999, CurrencyCode.PEN), Money(1_000, CurrencyCode.PEN)),
            ).map { NewEvent(it, day) },
        )
    }

    private fun viewModel(reconciliation: String? = "r1"): DiagnosticExportViewModel {
        val select = SelectDiagnosticEventsUseCase(store)
        val export = ExportDiagnosticUseCase(
            select, EventAnonymizer(), FakeSnapshots(), DiagnosticSummaryRenderer { ByteArray(4) },
            DiagnosticPackageBuilder(1_000), files, EventRecorder(store, ids, clock), clock, ids, "test", "14",
        )
        return DiagnosticExportViewModel(reconciliation, select, export, security, authenticator, guard, dispatcher)
    }

    private fun exports() = store.events.filter { it.event.type == "DiagnosticExportCreated" }

    @Test
    fun startsAnonymizedWithoutPasswordAndShowsThePreview() {
        seed()
        val state = viewModel().uiState.value

        assertEquals(ExportStage.FORM, state.stage)
        assertTrue(state.anonymize)
        assertFalse(state.usePassword)
        assertEquals(DiagnosticScope.RECONCILIATION, state.scope)
        assertEquals(2, state.preview?.eventCount)
        assertTrue(state.preview?.anonymizedFields?.isNotEmpty() == true)
    }

    @Test
    fun changingTheScopeRefreshesThePreview() {
        seed()
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.SetScope(DiagnosticScope.ACCOUNT_HISTORY))
        assertEquals(DiagnosticScope.ACCOUNT_HISTORY, vm.uiState.value.scope)
        assertNotNull(vm.uiState.value.preview)
    }

    @Test
    fun withoutAReconciliationOnlyTheFullScopeIsOffered() {
        seed()
        val state = viewModel(reconciliation = null).uiState.value
        assertEquals(DiagnosticScope.FULL, state.scope)
        assertEquals(listOf(DiagnosticScope.FULL), state.availableScopes)
    }

    @Test
    fun anUnknownReconciliationShowsNotFound() {
        seed()
        assertEquals(ExportMessage.NOT_FOUND, viewModel("nope").uiState.value.message)
    }

    @Test
    fun aShortPasswordIsRejectedBeforeAskingForTheCredential() {
        seed()
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.SetUsePassword(true))
        vm.onAction(DiagnosticExportUiAction.Submit("short".toCharArray(), "short".toCharArray(), prompt))
        assertEquals(ExportMessage.PASSWORD_TOO_SHORT, vm.uiState.value.message)
        assertEquals(0, authenticator.calls)
        assertEquals(ExportStage.FORM, vm.uiState.value.stage)
    }

    @Test
    fun differentPasswordsAreRejected() {
        seed()
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.SetUsePassword(true))
        vm.onAction(DiagnosticExportUiAction.Submit("correct horse battery".toCharArray(), "correct horse batterx".toCharArray(), prompt))
        assertEquals(ExportMessage.PASSWORD_MISMATCH, vm.uiState.value.message)
    }

    @Test
    fun withTheLockOnTheCredentialIsAskedAndFailureStopsTheExport() {
        seed()
        security.state.value = SecuritySettings(lockEnabled = true)
        authenticator.result = AuthResult.Failed
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.Submit(CharArray(0), CharArray(0), prompt))

        assertEquals(1, authenticator.calls)
        assertEquals(ExportStage.FORM, vm.uiState.value.stage)
        assertEquals(ExportMessage.AUTH_FAILED, vm.uiState.value.message)
    }

    @Test
    fun withoutAvailableAuthenticationTheExportContinuesLikeTheBackupDoes() {
        seed()
        security.state.value = SecuritySettings(lockEnabled = true)
        authenticator.availability = AuthAvailability.NO_CREDENTIAL_ENROLLED
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.Submit(CharArray(0), CharArray(0), prompt))
        assertEquals(ExportStage.PICKING, vm.uiState.value.stage)
    }

    @Test
    fun theFullFlowWritesTheFileAndEmitsTheEventOnlyAfterThePickerReturns() {
        seed()
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.Submit(CharArray(0), CharArray(0), prompt))
        assertEquals(ExportStage.PICKING, vm.uiState.value.stage)
        assertTrue(exports().isEmpty())

        vm.onAction(DiagnosticExportUiAction.PickerOpened)
        assertTrue(guard.isActive())
        vm.onAction(DiagnosticExportUiAction.PickerResult("doc://1"))

        assertFalse(guard.isActive())
        assertEquals(ExportStage.DONE, vm.uiState.value.stage)
        assertNotNull(vm.uiState.value.created)
        assertEquals(1, exports().size)
    }

    @Test
    fun cancellingThePickerLeavesNoEventAndReturnsToTheForm() {
        seed()
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.Submit(CharArray(0), CharArray(0), prompt))
        vm.onAction(DiagnosticExportUiAction.PickerOpened)
        vm.onAction(DiagnosticExportUiAction.PickerResult(null))

        assertEquals(ExportStage.FORM, vm.uiState.value.stage)
        assertTrue(exports().isEmpty())
        assertFalse(guard.isActive())
        assertNull(vm.uiState.value.created)
    }

    @Test
    fun aFailedVerificationShowsAMessageAndEmitsNothing() {
        seed()
        files.corruptOnRead = true
        val vm = viewModel()
        vm.onAction(DiagnosticExportUiAction.Submit(CharArray(0), CharArray(0), prompt))
        vm.onAction(DiagnosticExportUiAction.PickerOpened)
        vm.onAction(DiagnosticExportUiAction.PickerResult("doc://1"))

        assertEquals(ExportMessage.VERIFY_FAILED, vm.uiState.value.message)
        assertTrue(exports().isEmpty())
    }

    @Test
    fun thePasswordIsWipedAfterTheExport() {
        seed()
        val vm = viewModel()
        val password = "correct horse battery".toCharArray()
        val confirmation = password.copyOf()
        vm.onAction(DiagnosticExportUiAction.SetUsePassword(true))
        vm.onAction(DiagnosticExportUiAction.Submit(password, confirmation, prompt))
        vm.onAction(DiagnosticExportUiAction.PickerOpened)
        vm.onAction(DiagnosticExportUiAction.PickerResult("doc://1"))

        assertTrue(password.all { it == '\u0000' })
        assertTrue(confirmation.all { it == '\u0000' })
        assertEquals(1, exports().size)
    }
}
