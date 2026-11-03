package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import com.al3xiel.my_finances.core.navigation.Onboarding
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class OnboardingFlowUiState(val stack: List<Route> = emptyList()) {
    val canGoBack: Boolean get() = stack.size > 1
}

sealed interface OnboardingFlowUiAction {
    data class Start(val step: OnboardingStep) : OnboardingFlowUiAction
    data class Push(val route: Route) : OnboardingFlowUiAction

    /** Reemplaza todo el pasado: tras crear las cuentas, S03 no se vuelve a alcanzar con Atrás. */
    data class Replace(val route: Route) : OnboardingFlowUiAction
    data object Back : OnboardingFlowUiAction
}

/** La pila del flujo vive aquí; si el proceso muere, el arranque la reconstruye según cuentas y bandera (D60). */
@HiltViewModel
class OnboardingFlowViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingFlowUiState())
    val uiState: StateFlow<OnboardingFlowUiState> = _uiState.asStateFlow()

    fun onAction(action: OnboardingFlowUiAction) {
        when (action) {
            is OnboardingFlowUiAction.Start -> _uiState.update {
                if (it.stack.isNotEmpty()) it else it.copy(stack = listOf(action.step.route()))
            }
            is OnboardingFlowUiAction.Push -> _uiState.update { it.copy(stack = it.stack + action.route) }
            is OnboardingFlowUiAction.Replace -> _uiState.update { it.copy(stack = listOf(action.route)) }
            OnboardingFlowUiAction.Back -> _uiState.update { if (it.canGoBack) it.copy(stack = it.stack.dropLast(1)) else it }
        }
    }

    private fun OnboardingStep.route(): Route = when (this) {
        OnboardingStep.Security -> Onboarding.Security
        else -> Onboarding.Welcome
    }
}
