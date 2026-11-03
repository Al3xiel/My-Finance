package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PillButtonTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun everyStyleHasATouchTargetOfAtLeast48dp() {
        rule.setContent {
            MyfinancesTheme {
                androidx.compose.foundation.layout.Column {
                    PillButton("Primario", onClick = {}, style = PillStyle.PRIMARY)
                    PillButton("Tonal", onClick = {}, style = PillStyle.TONAL)
                    PillButton("Texto", onClick = {}, style = PillStyle.TEXT)
                }
            }
        }
        listOf("Primario", "Tonal", "Texto").forEach { label ->
            rule.onNodeWithText(label).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test fun clickingCallsOnClick() {
        var clicks = 0
        rule.setContent { MyfinancesTheme { PillButton("Guardar", onClick = { clicks++ }) } }
        rule.onNodeWithText("Guardar").assertIsEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test fun aDisabledButtonIgnoresTheTap() {
        var clicks = 0
        rule.setContent { MyfinancesTheme { PillButton("Guardar", onClick = { clicks++ }, enabled = false) } }
        rule.onNodeWithText("Guardar").assertIsNotEnabled().performClick()
        assertEquals(0, clicks)
    }
}
