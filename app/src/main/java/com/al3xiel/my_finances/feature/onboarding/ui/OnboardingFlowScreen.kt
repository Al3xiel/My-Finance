package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.al3xiel.my_finances.core.navigation.Backup
import com.al3xiel.my_finances.core.navigation.Onboarding
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.feature.onboarding.ui.components.OnboardingProgress
import com.al3xiel.my_finances.feature.onboarding.ui.components.onboardingStepNumber
import com.al3xiel.my_finances.core.navigation.TabPlaceholderScreen
import com.al3xiel.my_finances.feature.backup.ui.BackupScreen
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep

/** S02–S05 como flujo propio, sin pestañas ni FAB. «Restaurar» abre S70 tal cual: su reinicio de proceso cae en S04 (D61). */
@Composable
fun OnboardingFlow(
    start: OnboardingStep,
    modifier: Modifier = Modifier,
    viewModel: OnboardingFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(start) { viewModel.onAction(OnboardingFlowUiAction.Start(start)) }
    if (state.stack.isEmpty()) return

    BackHandler(enabled = state.canGoBack) { viewModel.onAction(OnboardingFlowUiAction.Back) }
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.safeDrawingPadding()) {
            onboardingStepNumber(state.stack.last())?.let { OnboardingProgress(step = it) }
            NavDisplay(
                backStack = state.stack,
                onBack = { viewModel.onAction(OnboardingFlowUiAction.Back) },
                entryProvider = onboardingEntries(viewModel::onAction),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun onboardingEntries(onAction: (OnboardingFlowUiAction) -> Unit): (Route) -> NavEntry<Route> =
    entryProvider(fallback = { route -> NavEntry(route) { TabPlaceholderScreen() } }) {
        entry<Onboarding.Welcome> {
            WelcomeScreen(
                onContinue = { onAction(OnboardingFlowUiAction.Push(Onboarding.Accounts)) },
                onRestore = { onAction(OnboardingFlowUiAction.Push(Backup)) },
            )
        }
        entry<Onboarding.Accounts> {
            AccountsSetupScreen(onCreated = { onAction(OnboardingFlowUiAction.Replace(Onboarding.Security)) })
        }
        entry<Backup> { BackupScreen() }
        entry<Onboarding.Security> {
            SecuritySetupScreen(onSaved = { onAction(OnboardingFlowUiAction.Push(Onboarding.Backup)) })
        }
        entry<Onboarding.Backup> {
            BackupSetupScreen(onOpenBackup = { onAction(OnboardingFlowUiAction.Push(Backup)) })
        }
    }
