package com.al3xiel.my_finances.core.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CatalogScaffoldTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun theAddActionLivesInTheHeaderAndShowsItsContent() {
        var added = 0
        rule.setContent {
            MyfinancesTheme {
                CatalogScaffold(title = "Personas", onAdd = { added++ }, isEmpty = false) { Text("Mamá") }
            }
        }
        rule.onNodeWithText("Mamá").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.catalog_add)).performClick()
        assertEquals(1, added)
    }

    @Test
    fun anEmptyCatalogShowsTheEmptyStateInsteadOfTheContent() {
        rule.setContent {
            MyfinancesTheme { CatalogScaffold(title = "Personas", onAdd = {}, isEmpty = true) { Text("Mamá") } }
        }
        rule.onNodeWithText(string(R.string.catalog_empty_title)).assertIsDisplayed()
        rule.onNodeWithText("Mamá").assertDoesNotExist()
    }
}
