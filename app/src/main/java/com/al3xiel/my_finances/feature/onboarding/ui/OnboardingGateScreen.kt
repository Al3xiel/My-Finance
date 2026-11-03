package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep

/** Entre el bloqueo y la app: mientras se resuelve el primer uso, [content] no se compone. */
@Composable
fun OnboardingGate(
    modifier: Modifier = Modifier,
    viewModel: OnboardingGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OnboardingGateContent(
        state = state,
        flow = { start -> OnboardingFlow(start = start, modifier = modifier) },
        modifier = modifier,
        content = content,
    )
}

@Composable
fun OnboardingGateContent(
    state: OnboardingGateState,
    flow: @Composable (OnboardingStep) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    when (state) {
        OnboardingGateState.Loading -> Surface(modifier = modifier.fillMaxSize()) {}
        is OnboardingGateState.Flow -> flow(state.start)
        OnboardingGateState.App -> content()
    }
}
