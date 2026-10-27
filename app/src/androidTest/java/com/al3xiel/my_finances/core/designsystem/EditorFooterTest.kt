package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
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
}
