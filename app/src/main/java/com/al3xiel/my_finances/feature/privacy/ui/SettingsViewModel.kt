package com.al3xiel.my_finances.feature.privacy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.navigation.SettingsSection
import com.al3xiel.my_finances.core.navigation.SettingsSectionRegistry
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.feature.privacy.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val themeMode: ThemeMode = ThemeMode.SYSTEM, val dynamicColor: Boolean = false)

sealed interface SettingsUiAction {
    data class SetThemeMode(val mode: ThemeMode) : SettingsUiAction
    data class SetDynamicColor(val enabled: Boolean) : SettingsUiAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
    sectionRegistry: SettingsSectionRegistry,
) : ViewModel() {

    /** Las secciones que aportan otras features (recordatorios, respaldo…); no cambian mientras la app corre. */
    val sections: List<SettingsSection> = sectionRegistry.sections

    val uiState: StateFlow<SettingsUiState> = combine(themeRepository.themeMode, themeRepository.dynamicColor, ::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onAction(action: SettingsUiAction) {
        when (action) {
            is SettingsUiAction.SetThemeMode -> viewModelScope.launch { themeRepository.setThemeMode(action.mode) }
            is SettingsUiAction.SetDynamicColor -> viewModelScope.launch { themeRepository.setDynamicColor(action.enabled) }
        }
    }
}
