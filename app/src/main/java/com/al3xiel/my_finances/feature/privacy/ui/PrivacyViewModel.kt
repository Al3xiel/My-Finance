package com.al3xiel.my_finances.feature.privacy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import com.al3xiel.my_finances.feature.privacy.domain.usecase.WipeAllDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Frase que hay que escribir para confirmar el borrado total. */
const val WIPE_CONFIRMATION_PHRASE = "BORRAR"

enum class WipeStage { NONE, WARNING, CONFIRM_PHRASE, WIPING, DONE, INCOMPLETE }

enum class PrivacyMessage { NO_CREDENTIAL, AUTH_FAILED }

data class PrivacyUiState(
    val settings: SecuritySettings = SecuritySettings(),
    val canEnableLock: Boolean = true,
    val wipeStage: WipeStage = WipeStage.NONE,
    val typedPhrase: String = "",
    val message: PrivacyMessage? = null,
)

sealed interface PrivacyUiAction {
    data class SetLockEnabled(val enabled: Boolean, val prompt: AuthPrompt) : PrivacyUiAction
    data class SetAutoLockMinutes(val minutes: Int) : PrivacyUiAction
    data class SetSecureScreen(val enabled: Boolean) : PrivacyUiAction
    data class SetHideAmounts(val enabled: Boolean) : PrivacyUiAction
    data object RefreshAvailability : PrivacyUiAction
    data object DismissMessage : PrivacyUiAction
    data object StartWipe : PrivacyUiAction
    data class ContinueWipe(val prompt: AuthPrompt) : PrivacyUiAction
    data class PhraseChanged(val text: String) : PrivacyUiAction
    data object ConfirmWipe : PrivacyUiAction
    data object CancelWipe : PrivacyUiAction
}

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val settingsRepository: SecuritySettingsRepository,
    private val authenticator: Authenticator,
    private val wipeAllData: WipeAllDataUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacyUiState(canEnableLock = canAuthenticate()))
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings -> _uiState.update { it.copy(settings = settings) } }
        }
    }

    fun onAction(action: PrivacyUiAction) {
        when (action) {
            is PrivacyUiAction.SetLockEnabled -> setLock(action.enabled, action.prompt)
            is PrivacyUiAction.SetAutoLockMinutes -> launch { settingsRepository.setAutoLockMinutes(action.minutes) }
            is PrivacyUiAction.SetSecureScreen -> launch { settingsRepository.setSecureScreen(action.enabled) }
            is PrivacyUiAction.SetHideAmounts -> launch { settingsRepository.setHideAmounts(action.enabled) }
            PrivacyUiAction.RefreshAvailability -> _uiState.update { it.copy(canEnableLock = canAuthenticate()) }
            PrivacyUiAction.DismissMessage -> _uiState.update { it.copy(message = null) }
            PrivacyUiAction.StartWipe -> _uiState.update { it.copy(wipeStage = WipeStage.WARNING, typedPhrase = "") }
            is PrivacyUiAction.ContinueWipe -> continueWipe(action.prompt)
            is PrivacyUiAction.PhraseChanged -> _uiState.update { it.copy(typedPhrase = action.text) }
            PrivacyUiAction.ConfirmWipe -> confirmWipe()
            PrivacyUiAction.CancelWipe -> _uiState.update { it.copy(wipeStage = WipeStage.NONE, typedPhrase = "") }
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    private fun canAuthenticate() = authenticator.availability() == AuthAvailability.READY

    private fun setLock(enabled: Boolean, prompt: AuthPrompt) {
        val canAuthenticate = canAuthenticate()
        _uiState.update { it.copy(canEnableLock = canAuthenticate, message = null) }
        if (enabled) {
            if (!canAuthenticate) {
                _uiState.update { it.copy(message = PrivacyMessage.NO_CREDENTIAL) }
                return
            }
            launch { settingsRepository.setLockEnabled(true) }
            return
        }
        viewModelScope.launch {
            // Sin credencial no hay con qué autenticar: el bloqueo ya no protege nada.
            if (!canAuthenticate || authenticate(prompt)) settingsRepository.setLockEnabled(false)
        }
    }

    private fun continueWipe(prompt: AuthPrompt) {
        if (_uiState.value.wipeStage != WipeStage.WARNING) return
        viewModelScope.launch {
            // Sin credencial en el dispositivo no hay con qué autenticar; borrar sigue siendo posible.
            val proceed = !canAuthenticate() || authenticate(prompt)
            if (proceed) _uiState.update { it.copy(wipeStage = WipeStage.CONFIRM_PHRASE) }
        }
    }

    /** `true` si el usuario se autenticó; si falla (no si cancela) deja un mensaje. */
    private suspend fun authenticate(prompt: AuthPrompt): Boolean = when (authenticator.authenticate(prompt)) {
        AuthResult.Success -> true
        AuthResult.Cancelled -> false
        AuthResult.LockedOut, AuthResult.Failed -> {
            _uiState.update { it.copy(message = PrivacyMessage.AUTH_FAILED) }
            false
        }
    }

    private fun confirmWipe() {
        val state = _uiState.value
        if (state.wipeStage != WipeStage.CONFIRM_PHRASE) return
        if (!state.typedPhrase.trim().equals(WIPE_CONFIRMATION_PHRASE, ignoreCase = true)) return
        _uiState.update { it.copy(wipeStage = WipeStage.WIPING) }
        viewModelScope.launch {
            val result = wipeAllData()
            _uiState.update { it.copy(wipeStage = if (result.isComplete) WipeStage.DONE else WipeStage.INCOMPLETE) }
        }
    }
}
