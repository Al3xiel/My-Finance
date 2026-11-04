package com.al3xiel.my_finances.core.designsystem

import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.domain.model.BusinessError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorFooterTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun saveButtonDispatchesTheAction() {
        var saves = 0
        rule.setContent { EditorFooter(error = null, onSave = { saves++ }) }
        rule.onNodeWithText(string(R.string.editor_save)).performClick()
        assertEquals(1, saves)
    }

    @Test
    fun withoutAnErrorNothingIsAnnounced() {
        rule.setContent { EditorFooter(error = null, onSave = {}) }
        rule.onNodeWithText(string(R.string.error_invalid_amount)).assertDoesNotExist()
    }

    @Test
    fun aRejectedSaveShowsTheErrorAsAPoliteLiveRegion() {
        rule.setContent { EditorFooter(error = BusinessError.InvalidAmount, onSave = {}) }
        rule.onNodeWithText(string(R.string.error_invalid_amount))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun saveIsAPrimaryPillOfAtLeast48dp() {
        rule.setContent { EditorFooter(error = null, onSave = {}) }
        rule.onNodeWithText(string(R.string.editor_save)).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun whileSavingTheButtonSaysSoAndIsBlocked() {
        var saves = 0
        rule.setContent { MyfinancesTheme { EditorFooter(error = null, onSave = { saves++ }, saving = true) } }
        rule.onNodeWithText(string(R.string.editor_saving)).assertIsDisplayed().assertIsNotEnabled().performClick()
        rule.onNodeWithText(string(R.string.editor_save)).assertDoesNotExist()
        assertEquals(0, saves)
    }

    @Test
    fun aBlockedSaveExplainsWhyInVisibleText() {
        rule.setContent {
            MyfinancesTheme { EditorFooter(error = null, onSave = {}, enabled = false, disabledReason = "Escribe qué quieres registrar") }
        }
        rule.onNodeWithText("Escribe qué quieres registrar").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.editor_save)).assertIsNotEnabled()
    }

    @Test
    fun anEnabledSaveShowsNoReason() {
        rule.setContent { MyfinancesTheme { EditorFooter(error = null, onSave = {}, disabledReason = "Escribe qué quieres registrar") } }
        rule.onNodeWithText("Escribe qué quieres registrar").assertDoesNotExist()
    }
}
