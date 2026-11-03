package com.al3xiel.my_finances.feature.diagnostics.ui

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.ExternalFlowGuard
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.model.event.AccountCreated
import com.al3xiel.my_finances.domain.model.event.AccountType
import com.al3xiel.my_finances.domain.model.event.BalanceCheckRecorded
import com.al3xiel.my_finances.domain.model.event.DiagnosticExportCreated
import com.al3xiel.my_finances.domain.model.event.DiagnosticScope
import com.al3xiel.my_finances.domain.model.event.DictionaryScope
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.feature.diagnostics.data.DiagnosticPackageBuilder
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryFormat
import com.al3xiel.my_finances.feature.diagnostics.domain.anonymize.EventAnonymizer
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DictionaryRequestCodec
import com.al3xiel.my_finances.feature.diagnostics.domain.model.DiagnosticSelectionRequest
import com.al3xiel.my_finances.feature.diagnostics.domain.repository.DiagnosticSummaryRenderer
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDiagnosticUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ExportDictionaryUseCase
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.FakeDiagnosticFiles
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.FakeSnapshots
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.ResolveAliasesUseCase
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
class DictionaryExportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val prompt = AuthPrompt("Confirma")
    private val store = FakeEventStore()
    private val files = FakeDiagnosticFiles()
    private val security = FakeSecuritySettingsRepository(SecuritySettings(lockEnabled = false))
    private val authenticator = FakeAuthenticator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val ids = SequentialIdGenerator()
    private val guard = ExternalFlowGuard(object : MonotonicClock { override fun nowMillis() = 0L })
    private val password = "correct horse battery".toCharArray()
    private val recorder = EventRecorder(store, ids, clock)
    private lateinit var export: DiagnosticExportCreated

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        runBlocking {
            store.append(
                "c",
                listOf(
                    AccountCreated(AccountId("a1"), "Cuenta Secreta", AccountType.BANK, CurrencyCode.PEN, null, Money(0, CurrencyCode.PEN)),
                    BalanceCheckRecorded(ReconciliationId("r1"), AccountId("a1"), Money(9_999, CurrencyCode.PEN), Money(1_000, CurrencyCode.PEN)),
                ).map { NewEvent(it, LocalDate.of(2026, 10, 25)) },
            )
            ExportDiagnosticUseCase(
                SelectDiagnosticEventsUseCase(store), EventAnonymizer(), FakeSnapshots(), DiagnosticSummaryRenderer { ByteArray(4) },
                DiagnosticPackageBuilder(1_000), FakeDiagnosticFiles(), recorder, clock, ids, "test", "14",
            )(DiagnosticSelectionRequest(DiagnosticScope.RECONCILIATION, ReconciliationId("r1")), "doc://pkg", null)
        }
        export = store.events.first { it.event.type == "DiagnosticExportCreated" }.event as DiagnosticExportCreated
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(reconciliation: String = "r1") = DictionaryExportViewModel(
        reconciliation,
        ExportDictionaryUseCase(store, SelectDiagnosticEventsUseCase(store), files, recorder, ids, ResolveAliasesUseCase(store), iterations = 1_000),
        security, authenticator, guard, dispatcher,
    )

    private fun code(prefix: String = export.packageHash.take(12)) = DictionaryRequestCodec.encode(
        DictionaryRequest(export.exportId, prefix, listOf(export.pseudonymMap.keys.first()), emptyList()),
    )

    private fun events() = store.events.filter { it.event.type == "DiagnosticDictionaryExported" }

    private fun DictionaryExportViewModel.submit(secret: CharArray = password.copyOf(), confirmation: CharArray = password.copyOf()) =
        onAction(DictionaryExportUiAction.Submit(secret, confirmation, prompt))

    @Test
    fun anInvalidCodeShowsNoPreview() {
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode("garbage"))
        assertNull(vm.uiState.value.preview)
        assertEquals(DictionaryMessage.INVALID_REQUEST, vm.uiState.value.message)
    }

    @Test
    fun aValidCodeListsExactlyWhatWillBeRevealed() {
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code()))
        assertEquals(listOf(export.pseudonymMap.keys.first()), vm.uiState.value.preview?.pseudonyms)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun aCodeForAnotherPackageIsRefused() {
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code(prefix = "000000000000")))
        assertEquals(DictionaryMessage.PACKAGE_MISMATCH, vm.uiState.value.message)
    }

    @Test
    fun theDictionaryCannotBeExportedFromAnotherReconciliation() {
        val vm = viewModel("r2")
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code()))
        assertEquals(DictionaryMessage.NOT_FROM_RECONCILIATION, vm.uiState.value.message)
    }

    @Test
    fun theFullScopeListsEveryPseudonym() {
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code()))
        vm.onAction(DictionaryExportUiAction.SetScope(DictionaryScope.FULL))
        assertEquals(export.pseudonymMap.keys.sorted(), vm.uiState.value.preview?.pseudonyms)
    }

    @Test
    fun theDictionaryAlwaysNeedsAPassword() {
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code()))
        vm.submit("short".toCharArray(), "short".toCharArray())
        assertEquals(DictionaryMessage.PASSWORD_TOO_SHORT, vm.uiState.value.message)
        assertEquals(DictionaryStage.FORM, vm.uiState.value.stage)
        vm.submit(password.copyOf(), "correct horse batterx".toCharArray())
        assertEquals(DictionaryMessage.PASSWORD_MISMATCH, vm.uiState.value.message)
    }

    @Test
    fun aFailedCredentialStopsBeforeThePicker() {
        security.state.value = SecuritySettings(lockEnabled = true)
        authenticator.result = AuthResult.Failed
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code()))
        vm.submit()
        assertEquals(DictionaryStage.FORM, vm.uiState.value.stage)
        assertEquals(DictionaryMessage.AUTH_FAILED, vm.uiState.value.message)
    }

    @Test
    fun theFullFlowSavesTheDictionaryAndEmitsTheEventAfterwards() {
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code()))
        val secret = password.copyOf()
        vm.submit(secret)
        assertEquals(DictionaryStage.PICKING, vm.uiState.value.stage)
        assertTrue(events().isEmpty())

        vm.onAction(DictionaryExportUiAction.PickerOpened)
        vm.onAction(DictionaryExportUiAction.PickerResult("doc://key"))

        assertEquals(DictionaryStage.DONE, vm.uiState.value.stage)
        assertNotNull(vm.uiState.value.created)
        assertEquals(1, events().size)
        assertEquals(export.exportId, DictionaryFormat.read(files.files.getValue("doc://key"), password.copyOf()).exportId)
        assertTrue(secret.all { it == '\u0000' })
        assertFalse(guard.isActive())
    }

    @Test
    fun cancellingThePickerEmitsNothing() {
        val vm = viewModel()
        vm.onAction(DictionaryExportUiAction.SetRequestCode(code()))
        vm.submit()
        vm.onAction(DictionaryExportUiAction.PickerOpened)
        vm.onAction(DictionaryExportUiAction.PickerResult(null))
        assertEquals(DictionaryStage.FORM, vm.uiState.value.stage)
        assertTrue(events().isEmpty())
    }
}
