package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.feature.onboarding.domain.model.OnboardingStep
import org.junit.Rule
import org.junit.Test

class OnboardingGateTest {
    @get:Rule
    val rule = createComposeRule()

    private fun setGate(state: OnboardingGateState) = rule.setContent {
        OnboardingGateContent(state = state, flow = { Text("flow ${it.name}") }) { Text("app") }
    }

    @Test
    fun appIsNotComposedWhileResolving() {
        setGate(OnboardingGateState.Loading)
        rule.onNodeWithText("app").assertDoesNotExist()
        rule.onNodeWithText("flow Welcome").assertDoesNotExist()
    }

    @Test
    fun firstUseShowsTheFlowAtItsStartAndNotTheApp() {
        setGate(OnboardingGateState.Flow(OnboardingStep.Security))
        rule.onNodeWithText("flow Security").assertExists()
        rule.onNodeWithText("app").assertDoesNotExist()
    }

    @Test
    fun completedOnboardingShowsTheApp() {
        setGate(OnboardingGateState.App)
        rule.onNodeWithText("app").assertExists()
        rule.onNodeWithText("flow Welcome").assertDoesNotExist()
    }
}
