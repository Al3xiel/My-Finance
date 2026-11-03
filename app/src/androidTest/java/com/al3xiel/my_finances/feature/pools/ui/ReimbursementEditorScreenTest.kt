package com.al3xiel.my_finances.feature.pools.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReimbursementEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = ReimbursementEditorUiState(
        loaded = true,
        personId = PersonId("mom"),
        personOptions = listOf(EntityOption("mom", "Mamá"), EntityOption("dad", "Papá")),
        accountOptions = listOf(EntityOption("bbva", "BBVA"), EntityOption("cash", "Efectivo")),
        accountId = AccountId("bbva"),
        amountText = "150",
    )

    @Test
    fun personAndAccountAreChipsAndTappingSelects() {
        val actions = mutableListOf<ReimbursementEditorUiAction>()
        rule.setContent { MyfinancesTheme { ReimbursementEditorContent(state, actions::add, personLocked = false) } }
        rule.onNodeWithText("Papá").assertIsDisplayed().performClick()
        rule.onNodeWithText("Efectivo").assertIsDisplayed().performClick()
        assertEquals(
            listOf<ReimbursementEditorUiAction>(
                ReimbursementEditorUiAction.SelectPerson(PersonId("dad")),
                ReimbursementEditorUiAction.SelectAccount(AccountId("cash")),
            ),
            actions,
        )
    }

    @Test
    fun theSaveFooterIsVisibleAndSaves() {
        val actions = mutableListOf<ReimbursementEditorUiAction>()
        rule.setContent { MyfinancesTheme { ReimbursementEditorContent(state, actions::add, personLocked = true) } }
        val save = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.editor_save)
        rule.onNodeWithText(save).assertIsDisplayed().performClick()
        assertEquals(listOf<ReimbursementEditorUiAction>(ReimbursementEditorUiAction.Save), actions)
    }
}
