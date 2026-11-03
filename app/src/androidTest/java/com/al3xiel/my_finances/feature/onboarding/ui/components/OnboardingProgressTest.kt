package com.al3xiel.my_finances.feature.onboarding.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.core.navigation.Backup
import com.al3xiel.my_finances.core.navigation.Onboarding
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class OnboardingProgressTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun eachStepOfTheFlowHasItsNumber() {
        assertEquals(1, onboardingStepNumber(Onboarding.Welcome))
        assertEquals(2, onboardingStepNumber(Onboarding.Accounts))
        assertEquals(3, onboardingStepNumber(Onboarding.Security))
        assertEquals(4, onboardingStepNumber(Onboarding.Backup))
    }

    @Test fun theRestoreScreenIsNotAStep() {
        assertNull(onboardingStepNumber(Backup))
    }

    @Test fun itSaysWhichStepYouAreOnAsText() {
        rule.setContent { MyfinancesTheme { OnboardingProgress(step = 2) } }
        rule.onNodeWithText("Paso 2 de 4").assertIsDisplayed()
    }
}
