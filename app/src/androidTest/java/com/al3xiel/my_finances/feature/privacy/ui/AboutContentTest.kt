package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutContentTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun tappingTheVersionCountsTowardTheDeveloperTools() {
        val actions = mutableListOf<AboutUiAction>()
        rule.setContent { MyfinancesTheme { AboutContent(AboutUiState(version = "1.0"), { actions += it }, onOpenDevTools = {}) } }
        rule.onNodeWithText("Versión 1.0").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(AboutUiAction.VersionTapped, actions.single())
    }

    @Test fun theDeveloperToolsEntryAppearsOnlyOnceEnabled() {
        rule.setContent { MyfinancesTheme { AboutContent(AboutUiState(version = "1.0"), {}, onOpenDevTools = {}) } }
        rule.onNodeWithText("Herramientas de desarrollador").assertDoesNotExist()
    }

    @Test fun enabledToolsOpenFromTheirEntry() {
        var opened = 0
        rule.setContent { MyfinancesTheme { AboutContent(AboutUiState(version = "1.0", devToolsEnabled = true), {}, onOpenDevTools = { opened++ }) } }
        rule.onNodeWithText("Herramientas de desarrollador").performClick()
        assertEquals(1, opened)
    }
}
