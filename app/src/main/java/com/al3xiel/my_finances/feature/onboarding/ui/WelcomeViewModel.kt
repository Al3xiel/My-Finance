package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.backup.domain.usecase.CanRestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WelcomeUiState(val canRestore: Boolean = false)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    canRestore: CanRestoreUseCase,
) : ViewModel() {
    val uiState: StateFlow<WelcomeUiState> = flow { emit(canRestore()) }
        .map(::WelcomeUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, WelcomeUiState())
}
