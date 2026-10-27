package com.al3xiel.my_finances.feature.privacy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.core.security.LockPolicy
import com.al3xiel.my_finances.core.time.MonotonicClock
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppLockMessage { LOCKED_OUT, FAILED, NOT_AVAILABLE }

/** Arranca bloqueada: en arranque en frío el contenido no se muestra hasta autenticar. */
data class AppLockUiState(
    val isLocked: Boolean = true,
    val isAuthenticating: Boolean = false,
    val message: AppLockMessage? = null,
)

sealed interface AppLockUiAction {
    data class Unlock(val prompt: AuthPrompt) : AppLockUiAction
    data object AppBackgrounded : AppLockUiAction
    data object AppForegrounded : AppLockUiAction
}

@HiltViewModel
class AppLockViewModel @Inject constructor(
    settingsRepository: SecuritySettingsRepository,
    private val authenticator: Authenticator,
    private val clock: MonotonicClock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppLockUiState())
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    private var settings = SecuritySettings()
    private var backgroundedAtMillis: Long? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { latest ->
                settings = latest
                if (!latest.lockEnabled) _uiState.update { AppLockUiState(isLocked = false) }
            }
        }
    }

    fun onAction(action: AppLockUiAction) {
        when (action) {
            is AppLockUiAction.Unlock -> unlock(action.prompt)
            AppLockUiAction.AppBackgrounded -> backgroundedAtMillis = clock.nowMillis()
            AppLockUiAction.AppForegrounded -> relockIfNeeded()
        }
    }

    private fun relockIfNeeded() {
        val backgroundedAt = backgroundedAtMillis ?: return
        backgroundedAtMillis = null
        val mustLock = LockPolicy.shouldLock(
            lockEnabled = settings.lockEnabled,
            autoLockMinutes = settings.autoLockMinutes,
            backgroundedAtMillis = backgroundedAt,
            nowMillis = clock.nowMillis(),
        )
        if (mustLock) _uiState.update { it.copy(isLocked = true, message = null) }
    }

    private fun unlock(prompt: AuthPrompt) {
        val current = _uiState.value
        if (!current.isLocked || current.isAuthenticating) return
        if (authenticator.availability() != AuthAvailability.READY) {
            _uiState.update { it.copy(message = AppLockMessage.NOT_AVAILABLE) }
            return
        }
        _uiState.update { it.copy(isAuthenticating = true, message = null) }
        viewModelScope.launch {
            val result = authenticator.authenticate(prompt)
            _uiState.update {
                when (result) {
                    AuthResult.Success -> AppLockUiState(isLocked = false)
                    AuthResult.Cancelled -> it.copy(isAuthenticating = false)
                    AuthResult.LockedOut -> it.copy(isAuthenticating = false, message = AppLockMessage.LOCKED_OUT)
                    AuthResult.Failed -> it.copy(isAuthenticating = false, message = AppLockMessage.FAILED)
                }
            }
        }
    }
}
