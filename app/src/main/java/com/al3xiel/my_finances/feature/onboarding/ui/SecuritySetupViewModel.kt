package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SecuritySetupUiState(
    /** Hay huella, rostro o bloqueo de pantalla registrados: sin eso el bloqueo no protegería nada. */
    val lockAvailable: Boolean = true,
    val lockEnabled: Boolean = true,
    /** «Ocultar en recientes»: pantalla segura, sin capturas ni vista previa. */
    val secureScreen: Boolean = true,
    val saved: Boolean = false,
)

sealed interface SecuritySetupUiAction {
    data class SetLock(val enabled: Boolean) : SecuritySetupUiAction
    data class SetSecureScreen(val enabled: Boolean) : SecuritySetupUiAction
    data object Save : SecuritySetupUiAction

    /** La pantalla ya avanzó: Atrás desde S05 no debe volver a dispararlo. */
    data object SavedHandled : SecuritySetupUiAction
}

/** S04: muestra el estado real del bloqueo (activado por defecto desde el paso 1) y guarda al confirmar. */
@HiltViewModel
class SecuritySetupViewModel @Inject constructor(
    private val settings: SecuritySettingsRepository,
    authenticator: Authenticator,
) : ViewModel() {
    private val lockAvailable = authenticator.availability() == AuthAvailability.READY

    private val _uiState = MutableStateFlow(SecuritySetupUiState(lockAvailable = lockAvailable))
    val uiState: StateFlow<SecuritySetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val current = settings.settings.first()
            _uiState.update {
                it.copy(lockEnabled = current.lockEnabled && lockAvailable, secureScreen = current.secureScreen)
            }
        }
    }

    fun onAction(action: SecuritySetupUiAction) {
        when (action) {
            // Sin credencial no se activa: el aviso sigue visible.
            is SecuritySetupUiAction.SetLock -> _uiState.update { it.copy(lockEnabled = action.enabled && lockAvailable) }
            is SecuritySetupUiAction.SetSecureScreen -> _uiState.update { it.copy(secureScreen = action.enabled) }
            SecuritySetupUiAction.Save -> save()
            SecuritySetupUiAction.SavedHandled -> _uiState.update { it.copy(saved = false) }
        }
    }

    private fun save() {
        val current = _uiState.value
        viewModelScope.launch {
            settings.setLockEnabled(current.lockEnabled)
            settings.setSecureScreen(current.secureScreen)
            _uiState.update { it.copy(saved = true) }
        }
    }
}
