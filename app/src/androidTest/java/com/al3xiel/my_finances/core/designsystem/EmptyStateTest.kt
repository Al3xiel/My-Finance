package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EmptyStateTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsTitleAndBodyWithoutAnAction() {
        rule.setContent { EmptyState(title = "Nada", body = "Todavía no hay datos") }
        rule.onNodeWithText("Nada").assertIsDisplayed()
        rule.onNodeWithText("Todavía no hay datos").assertIsDisplayed()
    }

    @Test
    fun actionButtonDispatchesClick() {
        var clicks = 0
        rule.setContent {
            EmptyState(title = "Nada", body = "Cuerpo", actionLabel = "Crear", onAction = { clicks++ })
        }
        rule.onNodeWithText("Crear").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun anOptionalIconKeepsTheTexts() {
        rule.setContent { EmptyState(title = "Nada", body = "Cuerpo", icon = AppIcons.Add) }
        rule.onNodeWithText("Nada").assertIsDisplayed()
        rule.onNodeWithText("Cuerpo").assertIsDisplayed()
    }

    @Test
    fun theActionIsAPillOfAtLeast48dp() {
        rule.setContent { EmptyState(title = "Nada", body = "Cuerpo", actionLabel = "Crear", onAction = {}) }
        rule.onNodeWithText("Crear").assertHeightIsAtLeast(48.dp)
    }
}
