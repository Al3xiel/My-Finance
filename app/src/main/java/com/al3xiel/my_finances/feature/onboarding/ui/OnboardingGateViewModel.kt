package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep
import com.al3xiel.my_finances.feature.onboarding.domain.usecase.ResolveOnboardingStepUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn

sealed interface OnboardingGateState {
    /** Todavía no se sabe si es el primer uso: la app no se compone. */
    data object Loading : OnboardingGateState

    /** El flujo S02–S05 arranca en [start] y no se reinicia aunque el paso resuelto cambie (crear cuentas, por ejemplo). */
    data class Flow(val start: OnboardingStep) : OnboardingGateState

    data object App : OnboardingGateState
}

@HiltViewModel
class OnboardingGateViewModel @Inject constructor(
    resolveStep: ResolveOnboardingStepUseCase,
) : ViewModel() {
    val uiState: StateFlow<OnboardingGateState> = resolveStep()
        .runningFold<OnboardingStep, OnboardingGateState>(OnboardingGateState.Loading) { current, step ->
            when {
                step == OnboardingStep.Done -> OnboardingGateState.App
                current is OnboardingGateState.Flow -> current
                else -> OnboardingGateState.Flow(step)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingGateState.Loading)
}
