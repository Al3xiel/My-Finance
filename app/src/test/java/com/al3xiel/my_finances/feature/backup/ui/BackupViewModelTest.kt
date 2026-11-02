package com.al3xiel.my_finances.feature.backup.ui

import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.ExternalFlowGuard
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.ChainRow
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.domain.usecase.FakeRestoreMarker
import com.al3xiel.my_finances.domain.usecase.SequentialIdGenerator
import com.al3xiel.my_finances.domain.usecase.VerifyEventChainUseCase
import com.al3xiel.my_finances.feature.backup.data.BackupImporter
import com.al3xiel.my_finances.feature.backup.data.ChainIntegrityMonitor
import com.al3xiel.my_finances.feature.backup.data.StagingSink
import com.al3xiel.my_finances.feature.backup.data.format.TEST_PASSWORD
import com.al3xiel.my_finances.feature.backup.data.format.manifestFor
import com.al3xiel.my_finances.feature.backup.data.format.testChain
import com.al3xiel.my_finances.feature.backup.data.format.writeBackup
import com.al3xiel.my_finances.feature.backup.domain.FakeBackupSettings
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupFilePort
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.backup.domain.usecase.CanRestoreUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreBackupUseCase
import com.al3xiel.my_finances.feature.privacy.FakeSecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class MemoryBackupFiles : BackupFilePort {
    val files = mutableMapOf<String, ByteArray>()

    override suspend fun openOutput(uri: String): OutputStream {
        val buffer = ByteArrayOutputStream()
        return object : OutputStream() {
            override fun write(b: Int) = buffer.write(b)
            override fun write(source: ByteArray, offset: Int, length: Int) = buffer.write(source, offset, length)
            override fun close() {
                files[uri] = buffer.toByteArray()
            }
        }
    }

    override suspend fun openInput(uri: String): InputStream? = files[uri]?.let { ByteArrayInputStream(it) }
    override suspend fun sizeOf(uri: String): Long? = files[uri]?.size?.toLong()
    override suspend fun delete(uri: String) = files.remove(uri) != null
}

private class CountingScheduler : BackupReminderScheduler {
    var ensured = 0
    var cancelled = 0
    override fun ensureScheduled() { ensured++ }
    override fun cancel() { cancelled++ }
}

private class NoopSink : StagingSink {
    override suspend fun insert(rows: List<ChainRow>) = Unit
    override suspend fun commit() = Unit
    override suspend fun discard() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val prompt = AuthPrompt("Confirma")
    private val store = FakeEventStore()
    private val files = MemoryBackupFiles()
    private val settings = FakeBackupSettings(enabled = false)
    private val scheduler = CountingScheduler()
    private val security = FakeSecuritySettingsRepository(SecuritySettings(lockEnabled = false))
    private val authenticator = FakeAuthenticator()
    private val clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)
    private val guard = ExternalFlowGuard(object : MonotonicClock { override fun nowMillis() = 0L })
    private var restarted = 0
    private val marker = FakeRestoreMarker()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(): BackupViewModel {
        val ids = SequentialIdGenerator()
        return BackupViewModel(
            observeStatus = ObserveBackupStatusUseCase(store, settings, clock),
            monitor = ChainIntegrityMonitor(VerifyEventChainUseCase(store), dispatcher),
            canRestore = CanRestoreUseCase(store),
            createBackup = CreateBackupUseCase(store, files, EventRecorder(store, ids, clock), clock, ids, "t", 6, iterations = 1_000),
            restoreBackup = RestoreBackupUseCase(
                files,
                CanRestoreUseCase(store),
                BackupImporter({ NoopSink() }, ids, clock, "t"),
                { },
                marker,
                { restarted++ },
            ),
            settings = settings,
            scheduler = scheduler,
            securitySettings = security,
            authenticator = authenticator,
            externalFlow = guard,
            ioDispatcher = dispatcher,
        )
    }

    private suspend fun userData() {
        store.append("c", listOf(NewEvent(AccountRenamed(AccountId("a"), "x"), LocalDate.of(2026, 11, 1))))
    }

    private fun password() = "correct horse battery".toCharArray()

    private fun BackupViewModel.createWith(uri: String? = "doc://new", pw: CharArray = password(), confirm: CharArray = password()) {
        onAction(BackupUiAction.StartCreate)
        onAction(BackupUiAction.SubmitCreatePassword(pw, confirm, prompt))
        onAction(BackupUiAction.CreatePickerOpened)
        onAction(BackupUiAction.CreatePickerResult(uri))
    }

    private val fixtureBytes get() = testChain(4).let { writeBackup(manifestFor(it), it) }

    @Test
    fun aShortPasswordIsRefusedAndWiped() = runTest {
        val vm = viewModel()
        val short = "short".toCharArray()
        val confirm = "short".toCharArray()
        vm.onAction(BackupUiAction.StartCreate)
        vm.onAction(BackupUiAction.SubmitCreatePassword(short, confirm, prompt))

        assertEquals(BackupMessage.PASSWORD_TOO_SHORT, vm.uiState.value.message)
        assertEquals(BackupStage.CREATE_PASSWORD, vm.uiState.value.stage)
        assertTrue(short.all { it == '\u0000' } && confirm.all { it == '\u0000' })
    }

    @Test
    fun differentPasswordsAreRefused() = runTest {
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartCreate)
        vm.onAction(BackupUiAction.SubmitCreatePassword(password(), "another long password".toCharArray(), prompt))

        assertEquals(BackupMessage.PASSWORD_MISMATCH, vm.uiState.value.message)
        assertEquals(BackupStage.CREATE_PASSWORD, vm.uiState.value.stage)
    }

    @Test
    fun withTheLockOnCreatingAsksToAuthenticateFirst() = runTest {
        security.state.value = SecuritySettings(lockEnabled = true)
        authenticator.result = AuthResult.Cancelled
        val vm = viewModel()
        val pw = password()
        vm.onAction(BackupUiAction.StartCreate)
        vm.onAction(BackupUiAction.SubmitCreatePassword(pw, password(), prompt))

        assertEquals(1, authenticator.calls)
        assertEquals(BackupStage.IDLE, vm.uiState.value.stage)
        assertTrue(pw.all { it == '\u0000' })
    }

    @Test
    fun aFailedAuthenticationLeavesAMessageAndNoPicker() = runTest {
        security.state.value = SecuritySettings(lockEnabled = true)
        authenticator.result = AuthResult.Failed
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartCreate)
        vm.onAction(BackupUiAction.SubmitCreatePassword(password(), password(), prompt))

        assertEquals(BackupMessage.AUTH_FAILED, vm.uiState.value.message)
        assertEquals(BackupStage.IDLE, vm.uiState.value.stage)
    }

    @Test
    fun withoutTheLockNoAuthenticationIsAsked() = runTest {
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartCreate)
        vm.onAction(BackupUiAction.SubmitCreatePassword(password(), password(), prompt))

        assertEquals(0, authenticator.calls)
        assertEquals(BackupStage.CREATE_PICKING, vm.uiState.value.stage)
    }

    @Test
    fun theFullCreateFlowWritesTheFileAndKeepsTheGuardOnlyWhilePickerIsOpen() = runTest {
        userData()
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartCreate)
        vm.onAction(BackupUiAction.SubmitCreatePassword(password(), password(), prompt))
        vm.onAction(BackupUiAction.CreatePickerOpened)
        assertTrue(guard.isActive())
        assertEquals(BackupStage.CREATE_PICKER_OPEN, vm.uiState.value.stage)
        vm.onAction(BackupUiAction.CreatePickerResult("doc://new"))

        assertFalse(guard.isActive())
        assertEquals(BackupStage.CREATED, vm.uiState.value.stage)
        assertEquals(1L, vm.uiState.value.created?.eventCount)
        assertNotNull(files.files["doc://new"])
        assertEquals(1, store.events.count { it.event.type == "BackupCreated" })
    }

    @Test
    fun cancellingThePickerCreatesNothing() = runTest {
        userData()
        val vm = viewModel()
        vm.createWith(uri = null)

        assertEquals(BackupStage.IDLE, vm.uiState.value.stage)
        assertTrue(files.files.isEmpty())
        assertFalse(guard.isActive())
    }

    @Test
    fun restoreIsOfferedOnlyOnADatabaseWithoutUserData() = runTest {
        assertEquals(true, viewModel().uiState.value.canRestore)
        userData()
        assertEquals(false, viewModel().uiState.value.canRestore)
    }

    @Test
    fun startingARestoreWithDataExplainsInsteadOfOpeningThePicker() = runTest {
        userData()
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartRestore)

        assertEquals(BackupStage.IDLE, vm.uiState.value.stage)
        assertEquals(BackupMessage.RESTORE_NOT_ALLOWED, vm.uiState.value.message)
    }

    @Test
    fun theRestoreFlowShowsASummaryThenRestoresAndRestarts() = runTest {
        files.files["doc://old"] = fixtureBytes
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartRestore)
        vm.onAction(BackupUiAction.RestorePickerOpened)
        assertTrue(guard.isActive())
        vm.onAction(BackupUiAction.RestorePickerResult("doc://old"))
        assertFalse(guard.isActive())
        assertEquals(BackupStage.RESTORE_PASSWORD, vm.uiState.value.stage)

        val pw = TEST_PASSWORD.copyOf()
        vm.onAction(BackupUiAction.SubmitRestorePassword(pw))
        assertEquals(BackupStage.RESTORE_CONFIRM, vm.uiState.value.stage)
        assertEquals(4L, vm.uiState.value.summary?.eventCount)
        assertEquals(0, restarted)

        vm.onAction(BackupUiAction.ConfirmRestore)
        assertEquals(1, restarted)
        assertTrue(marker.pending)
        assertTrue(pw.all { it == '\u0000' })
    }

    @Test
    fun aWrongRestorePasswordShowsAMessageAndAsksAgain() = runTest {
        files.files["doc://old"] = fixtureBytes
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartRestore)
        vm.onAction(BackupUiAction.RestorePickerResult("doc://old"))
        vm.onAction(BackupUiAction.SubmitRestorePassword("not the password".toCharArray()))

        assertEquals(BackupMessage.WRONG_PASSWORD_OR_TAMPERED, vm.uiState.value.message)
        assertEquals(BackupStage.RESTORE_PASSWORD, vm.uiState.value.stage)
        assertEquals(0, restarted)
        assertNull(vm.uiState.value.summary)
    }

    @Test
    fun cancellingTheConfirmationRestoresNothing() = runTest {
        files.files["doc://old"] = fixtureBytes
        val vm = viewModel()
        vm.onAction(BackupUiAction.StartRestore)
        vm.onAction(BackupUiAction.RestorePickerResult("doc://old"))
        vm.onAction(BackupUiAction.SubmitRestorePassword(TEST_PASSWORD.copyOf()))
        vm.onAction(BackupUiAction.Cancel)

        assertEquals(BackupStage.IDLE, vm.uiState.value.stage)
        assertEquals(0, restarted)
        assertFalse(marker.pending)
    }

    @Test
    fun reminderOnWithPermissionSchedulesAndOffCancels() = runTest {
        val vm = viewModel()
        vm.onAction(BackupUiAction.ToggleReminder(on = true, permissionGranted = true))
        assertTrue(settings.isEnabled())
        assertEquals(1, scheduler.ensured)

        vm.onAction(BackupUiAction.ToggleReminder(on = false, permissionGranted = true))
        assertFalse(settings.isEnabled())
        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun reminderWithoutPermissionExplainsThenRequestsAndDeniedStaysOff() = runTest {
        val vm = viewModel()
        vm.onAction(BackupUiAction.ToggleReminder(on = true, permissionGranted = false))
        assertTrue(vm.uiState.value.showExplanation)
        assertFalse(settings.isEnabled())

        vm.onAction(BackupUiAction.AcceptExplanation)
        assertTrue(vm.uiState.value.requestPermission)
        vm.onAction(BackupUiAction.PermissionRequestLaunched)
        vm.onAction(BackupUiAction.PermissionResult(granted = false))

        assertTrue(vm.uiState.value.permissionDenied)
        assertFalse(settings.isEnabled())
        assertEquals(0, scheduler.ensured)
    }

    @Test
    fun grantingThePermissionTurnsTheReminderOn() = runTest {
        val vm = viewModel()
        vm.onAction(BackupUiAction.ToggleReminder(on = true, permissionGranted = false))
        vm.onAction(BackupUiAction.AcceptExplanation)
        vm.onAction(BackupUiAction.PermissionResult(granted = true))

        assertTrue(settings.isEnabled())
        assertEquals(1, scheduler.ensured)
    }

    @Test
    fun theIntervalIsStoredAndShown() = runTest {
        val vm = viewModel()
        vm.onAction(BackupUiAction.SetIntervalDays(30))
        assertEquals(30, vm.uiState.value.intervalDays)
    }

    @Test
    fun theStateShowsTheChainStatusAfterARecheck() = runTest {
        userData()
        val vm = viewModel()
        vm.onAction(BackupUiAction.RecheckChain)
        assertTrue(vm.uiState.value.chain is com.al3xiel.my_finances.feature.backup.data.ChainStatus.Intact)
    }
}
