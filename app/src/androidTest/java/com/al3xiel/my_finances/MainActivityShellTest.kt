package com.al3xiel.my_finances

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

/**
 * The unlocked path (five tabs visible) is covered by `MainScaffoldTest`: the biometric prompt cannot be
 * driven from here, and the gate only decides whether the scaffold is composed.
 */
class MainActivityShellTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun lockedAppDoesNotComposeTheScaffold() {
        rule.waitForIdle()
        rule.onNodeWithTag(APP_CONTENT_TAG).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.tab_home)).assertDoesNotExist()
    }
}
