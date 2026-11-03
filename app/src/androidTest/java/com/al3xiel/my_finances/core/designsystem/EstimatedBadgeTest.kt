package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class EstimatedBadgeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTheEstimatedLabelAsTextNotOnlyAsColor() {
        rule.setContent { EstimatedBadge() }
        rule.onNodeWithText("Estimado").assertIsDisplayed()
    }
}
