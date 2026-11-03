package com.al3xiel.my_finances.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.home.domain.model.HomeSummary
import com.al3xiel.my_finances.feature.home.domain.usecase.ObserveHomeSummaryUseCase
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `summary` es `null` mientras llegan las proyecciones. */
data class HomeUiState(
    val summary: HomeSummary? = null,
    val hideAmounts: Boolean = false,
)

sealed interface HomeUiAction {
    /** El ojo del encabezado: alterna el ajuste global y persiste. */
    data object ToggleHideAmounts : HomeUiAction
}

/** S10: solo lee proyecciones; lo único que escribe es el ajuste de ocultar montos. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHome: ObserveHomeSummaryUseCase,
    private val settings: SecuritySettingsRepository,
) : ViewModel() {
    private val hideAmounts = settings.settings.map { it.hideAmounts }

    val uiState: StateFlow<HomeUiState> = combine(observeHome(), hideAmounts) { summary, hide -> HomeUiState(summary, hide) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.ToggleHideAmounts -> {
                val next = !uiState.value.hideAmounts
                viewModelScope.launch { settings.setHideAmounts(next) }
            }
        }
    }
}
