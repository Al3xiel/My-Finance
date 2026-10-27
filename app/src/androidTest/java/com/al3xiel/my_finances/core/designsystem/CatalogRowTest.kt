package com.al3xiel.my_finances.core.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CatalogRowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    @Test
    fun tappingTheRowOffersRenameAndArchive() {
        rule.setContent { CatalogRow(title = "Comida", onRename = {}, onArchive = {}) }
        rule.onNodeWithText("Comida").performClick()
        rule.onNodeWithText(string(R.string.catalog_rename)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.catalog_archive)).assertIsDisplayed()
    }

    @Test
    fun theChosenActionIsDispatchedOnce() {
        val calls = mutableListOf<String>()
        rule.setContent { CatalogRow(title = "Comida", onRename = { calls += "rename" }, onArchive = { calls += "archive" }) }
        rule.onNodeWithText("Comida").performClick()
        rule.onNodeWithText(string(R.string.catalog_archive)).performClick()
        assertEquals(listOf("archive"), calls)
    }

    @Test
    fun aRowThatCannotBeEditedOffersNoMenu() {
        rule.setContent { CatalogRow(title = "Reembolso", subtitle = "Del sistema", onRename = {}, onArchive = {}, canEdit = false) }
        rule.onNodeWithText("Reembolso").performClick()
        rule.onNodeWithText(string(R.string.catalog_rename)).assertDoesNotExist()
    }
}
