package com.al3xiel.my_finances

import android.view.WindowManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivitySecurityTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun windowIsSecureByDefault() {
        rule.waitForIdle()
        val flags = rule.activity.window.attributes.flags
        assertTrue(flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
    }

    @Test
    fun appContentIsNotComposedWhileLocked() {
        rule.waitForIdle()
        rule.onNodeWithTag(APP_CONTENT_TAG).assertDoesNotExist()
    }
}
