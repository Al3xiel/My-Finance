package com.al3xiel.my_finances.feature.privacy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.feature.privacy.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val themeMode: ThemeMode = ThemeMode.SYSTEM)

sealed interface SettingsUiAction {
    data class SetThemeMode(val mode: ThemeMode) : SettingsUiAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeRepository: ThemeRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = themeRepository.themeMode
        .map { SettingsUiState(themeMode = it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onAction(action: SettingsUiAction) {
        when (action) {
            is SettingsUiAction.SetThemeMode -> viewModelScope.launch { themeRepository.setThemeMode(action.mode) }
        }
    }
}
