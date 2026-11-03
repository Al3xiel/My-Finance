package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SectionCardTest {
    @get:Rule
    val rule = createComposeRule()

    @Test fun showsTheSectionTitleAndItsRows() {
        rule.setContent {
            MyfinancesTheme {
                SectionCard(title = "Seguridad") {
                    SectionRow(headline = "Bloqueo", supporting = "Al abrir la app")
                    SectionRow(headline = "Pantalla segura")
                }
            }
        }
        rule.onNodeWithText("Seguridad").assertIsDisplayed()
        rule.onNodeWithText("Bloqueo").assertIsDisplayed()
        rule.onNodeWithText("Al abrir la app").assertIsDisplayed()
        rule.onNodeWithText("Pantalla segura").assertIsDisplayed()
    }

    @Test fun aCardWithoutTitleStillShowsItsContent() {
        rule.setContent { MyfinancesTheme { SectionCard { Text("Solo contenido") } } }
        rule.onNodeWithText("Solo contenido").assertIsDisplayed()
    }

    @Test fun aClickableRowReportsTheTapAndIsAtLeast48dpTall() {
        var taps = 0
        rule.setContent {
            MyfinancesTheme { SectionCard { SectionRow(headline = "Abrir", onClick = { taps++ }) } }
        }
        rule.onNodeWithText("Abrir").assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(1, taps)
    }

    @Test fun rowsShowTheirTrailingContent() {
        rule.setContent {
            MyfinancesTheme { SectionCard { SectionRow(headline = "Total", trailing = { Text("S/ 10.00") }) } }
        }
        rule.onNodeWithText("S/ 10.00").assertIsDisplayed()
    }
}
