package com.al3xiel.my_finances.feature.privacy.ui

import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.FakeAuthenticator
import com.al3xiel.my_finances.feature.privacy.FakeSecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import com.al3xiel.my_finances.feature.privacy.domain.usecase.WipeAllDataUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {

    private val prompt = AuthPrompt("Confirma")
    private val settings = FakeSecuritySettingsRepository()
    private val authenticator = FakeAuthenticator()
    private var wipes = 0
    private var wipeFails = false

    private val wipeStep = object : WipeStep {
        override val id = "test"
        override suspend fun wipe() {
            wipes++
            if (wipeFails) error("boom")
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        PrivacyViewModel(settings, authenticator, WipeAllDataUseCase(setOf(wipeStep), UnconfinedTestDispatcher()))

    private fun PrivacyViewModel.walkToPhraseStage() {
        onAction(PrivacyUiAction.StartWipe)
        onAction(PrivacyUiAction.ContinueWipe(prompt))
    }

    @Test
    fun exposesTheStoredSettings() = runTest {
        settings.setHideAmounts(true)
        assertTrue(viewModel().uiState.value.settings.hideAmounts)
    }

    @Test
    fun togglesPersistWithoutAuthenticating() = runTest {
        val vm = viewModel()
        vm.onAction(PrivacyUiAction.SetSecureScreen(false))
        vm.onAction(PrivacyUiAction.SetHideAmounts(true))
        vm.onAction(PrivacyUiAction.SetAutoLockMinutes(15))
        assertEquals(0, authenticator.calls)
        assertEquals(false, settings.state.value.secureScreen)
        assertEquals(true, settings.state.value.hideAmounts)
        assertEquals(15, settings.state.value.autoLockMinutes)
    }

    @Test
    fun disablingTheLockRequiresAuthentication() = runTest {
        val vm = viewModel()
        authenticator.result = AuthResult.Failed
        vm.onAction(PrivacyUiAction.SetLockEnabled(false, prompt))
        assertTrue(settings.state.value.lockEnabled)
        assertEquals(PrivacyMessage.AUTH_FAILED, vm.uiState.value.message)

        authenticator.result = AuthResult.Success
        vm.onAction(PrivacyUiAction.SetLockEnabled(false, prompt))
        assertFalse(settings.state.value.lockEnabled)
    }

    @Test
    fun cancellingTheAuthenticationKeepsTheLockWithoutMessage() = runTest {
        val vm = viewModel()
        authenticator.result = AuthResult.Cancelled
        vm.onAction(PrivacyUiAction.SetLockEnabled(false, prompt))
        assertTrue(settings.state.value.lockEnabled)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun theLockCannotBeEnabledWithoutADeviceCredential() = runTest {
        settings.setLockEnabled(false)
        authenticator.availability = AuthAvailability.NO_CREDENTIAL_ENROLLED
        val vm = viewModel()
        vm.onAction(PrivacyUiAction.SetLockEnabled(true, prompt))
        assertFalse(settings.state.value.lockEnabled)
        assertEquals(PrivacyMessage.NO_CREDENTIAL, vm.uiState.value.message)
        assertFalse(vm.uiState.value.canEnableLock)
    }

    @Test
    fun enablingTheLockWorksWhenTheDeviceCanAuthenticate() = runTest {
        settings.setLockEnabled(false)
        viewModel().onAction(PrivacyUiAction.SetLockEnabled(true, prompt))
        assertTrue(settings.state.value.lockEnabled)
    }

    @Test
    fun wipeNeedsWarningThenAuthenticationThenThePhrase() = runTest {
        val vm = viewModel()
        vm.onAction(PrivacyUiAction.StartWipe)
        assertEquals(WipeStage.WARNING, vm.uiState.value.wipeStage)
        vm.onAction(PrivacyUiAction.ContinueWipe(prompt))
        assertEquals(WipeStage.CONFIRM_PHRASE, vm.uiState.value.wipeStage)
        assertEquals(0, wipes)
    }

    @Test
    fun wipeDoesNotAdvanceWhenAuthenticationFails() = runTest {
        authenticator.result = AuthResult.Failed
        val vm = viewModel()
        vm.walkToPhraseStage()
        assertEquals(WipeStage.WARNING, vm.uiState.value.wipeStage)
        assertEquals(PrivacyMessage.AUTH_FAILED, vm.uiState.value.message)
    }

    @Test
    fun wrongPhraseDoesNotWipe() = runTest {
        val vm = viewModel()
        vm.walkToPhraseStage()
        vm.onAction(PrivacyUiAction.PhraseChanged("borrar algo"))
        vm.onAction(PrivacyUiAction.ConfirmWipe)
        assertEquals(0, wipes)
        assertEquals(WipeStage.CONFIRM_PHRASE, vm.uiState.value.wipeStage)
    }

    @Test
    fun theRightPhraseWipesAndFinishes() = runTest {
        val vm = viewModel()
        vm.walkToPhraseStage()
        vm.onAction(PrivacyUiAction.PhraseChanged("  borrar "))
        vm.onAction(PrivacyUiAction.ConfirmWipe)
        assertEquals(1, wipes)
        assertEquals(WipeStage.DONE, vm.uiState.value.wipeStage)
    }

    @Test
    fun aFailingStepLeavesTheWipeIncomplete() = runTest {
        wipeFails = true
        val vm = viewModel()
        vm.walkToPhraseStage()
        vm.onAction(PrivacyUiAction.PhraseChanged(WIPE_CONFIRMATION_PHRASE))
        vm.onAction(PrivacyUiAction.ConfirmWipe)
        assertEquals(WipeStage.INCOMPLETE, vm.uiState.value.wipeStage)
    }

    @Test
    fun cancellingResetsTheWipeFlow() = runTest {
        val vm = viewModel()
        vm.walkToPhraseStage()
        vm.onAction(PrivacyUiAction.PhraseChanged("BORR"))
        vm.onAction(PrivacyUiAction.CancelWipe)
        assertEquals(WipeStage.NONE, vm.uiState.value.wipeStage)
        assertEquals("", vm.uiState.value.typedPhrase)
    }

    @Test
    fun withoutDeviceCredentialTheWipeSkipsAuthentication() = runTest {
        authenticator.availability = AuthAvailability.UNSUPPORTED
        val vm = viewModel()
        vm.walkToPhraseStage()
        assertEquals(WipeStage.CONFIRM_PHRASE, vm.uiState.value.wipeStage)
        assertEquals(0, authenticator.calls)
    }
}
