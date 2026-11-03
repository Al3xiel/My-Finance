package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppLockContentTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun unlockButtonDispatchesUnlock() {
        val actions = mutableListOf<AppLockUiAction>()
        rule.setContent { AppLockContent(AppLockUiState(), onAction = { actions += it }) }
        rule.onNodeWithText(string(R.string.app_lock_unlock)).performClick()
        assertTrue(actions.single() is AppLockUiAction.Unlock)
    }

    @Test
    fun showsTheMessageForAFailedAttempt() {
        rule.setContent { AppLockContent(AppLockUiState(message = AppLockMessage.LOCKED_OUT), onAction = {}) }
        rule.onNodeWithText(string(R.string.app_lock_message_locked_out)).assertIsDisplayed()
    }
}
