package com.al3xiel.my_finances.feature.loans.ui

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

class LoanEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val state = LoanEditorUiState(
        loaded = true,
        personId = PersonId("pepe"),
        accountId = AccountId("bbva"),
        amountText = "50",
        personOptions = listOf(EntityOption("pepe", "Pepe"), EntityOption("ana", "Ana")),
        accountOptions = listOf(EntityOption("bbva", "BBVA"), EntityOption("cash", "Efectivo")),
    )

    @Test
    fun personAndAccountAreChipsAndTheFooterSaves() {
        val actions = mutableListOf<LoanEditorUiAction>()
        rule.setContent { MyfinancesTheme { LoanEditorContent(state, actions::add) } }
        rule.onNodeWithText("Ana").assertIsDisplayed().performClick()
        rule.onNodeWithText("Efectivo").assertIsDisplayed().performClick()
        val save = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.editor_save)
        rule.onNodeWithText(save).assertIsDisplayed().performClick()
        assertEquals(
            listOf<LoanEditorUiAction>(
                LoanEditorUiAction.SelectPerson(PersonId("ana")),
                LoanEditorUiAction.SelectAccount(AccountId("cash")),
                LoanEditorUiAction.Save,
            ),
            actions,
        )
    }
}
