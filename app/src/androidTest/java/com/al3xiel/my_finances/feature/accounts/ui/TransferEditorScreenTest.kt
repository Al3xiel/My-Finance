package com.al3xiel.my_finances.feature.accounts.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TransferEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val state = TransferEditorUiState(
        loaded = true,
        fromId = AccountId("a"),
        toId = AccountId("b"),
        amountText = "100",
        accountOptions = listOf(EntityOption("a", "BBVA"), EntityOption("b", "Efectivo"), EntityOption("c", "Yape")),
    )

    @Test
    fun originAndDestinationAreChipsAndTappingSelects() {
        val actions = mutableListOf<TransferEditorUiAction>()
        rule.setContent { MyfinancesTheme { TransferEditorContent(state, actions::add) } }
        // Cada cuenta aparece una vez por selector (desde y hacia).
        rule.onAllNodesWithText("Yape")[0].assertIsDisplayed().performClick()
        rule.onAllNodesWithText("Yape")[1].performClick()
        assertEquals(
            listOf<TransferEditorUiAction>(
                TransferEditorUiAction.SelectFrom(AccountId("c")),
                TransferEditorUiAction.SelectTo(AccountId("c")),
            ),
            actions,
        )
    }

    @Test
    fun theSaveFooterIsVisibleAndSaves() {
        val actions = mutableListOf<TransferEditorUiAction>()
        rule.setContent { MyfinancesTheme { TransferEditorContent(state, actions::add) } }
        rule.onNodeWithText(string(R.string.editor_save)).assertIsDisplayed().performClick()
        assertEquals(listOf<TransferEditorUiAction>(TransferEditorUiAction.Save), actions)
    }
}
