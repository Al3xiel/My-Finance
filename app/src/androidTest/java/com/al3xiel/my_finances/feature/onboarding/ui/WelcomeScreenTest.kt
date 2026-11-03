package com.al3xiel.my_finances.feature.onboarding.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun statesThatDataNeverLeavesThePhoneAndContinues() {
        var continued = 0
        rule.setContent { WelcomeContent(WelcomeUiState(), onContinue = { continued++ }, onRestore = {}) }

        rule.onNodeWithText(string(R.string.onboarding_welcome_privacy)).assertExists()
        rule.onNodeWithText(string(R.string.onboarding_continue)).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun hidesRestoreWhenNotAllowed() {
        rule.setContent { WelcomeContent(WelcomeUiState(canRestore = false), onContinue = {}, onRestore = {}) }
        rule.onNodeWithText(string(R.string.onboarding_restore)).assertDoesNotExist()
    }

    @Test
    fun offersRestoreWhenAllowed() {
        var restored = 0
        rule.setContent { WelcomeContent(WelcomeUiState(canRestore = true), onContinue = {}, onRestore = { restored++ }) }
        rule.onNodeWithText(string(R.string.onboarding_restore)).performClick()
        assertEquals(1, restored)
    }
}
