package com.al3xiel.my_finances.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

    @Test
    fun isTheSharedStatusBadge() {
        rule.setContent { Column { EstimatedBadge(); StatusBadge(BadgeStatus.ESTIMATED) } }
        rule.onAllNodesWithText("Estimado").assertCountEquals(2)
    }
}
