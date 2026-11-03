package com.al3xiel.my_finances.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RegisterFabTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val action = RegisterAction("expense", R.string.more_entry_privacy, Privacy, order = 1)

    private fun show(state: MainScaffoldUiState, onAction: (MainScaffoldUiAction) -> Unit = {}) =
        rule.setContent { MainScaffoldContent(state, onAction, rememberRouteEntryProvider(emptySet())) }

    @Test
    fun fabIsHiddenWithoutActions() {
        show(MainScaffoldUiState())
        rule.onNodeWithContentDescription(string(R.string.register_fab)).assertDoesNotExist()
    }

    @Test
    fun fabOpensTheSheet() {
        val actions = mutableListOf<MainScaffoldUiAction>()
        show(MainScaffoldUiState(registerActions = listOf(action)), onAction = { actions += it })
        rule.onNodeWithContentDescription(string(R.string.register_fab)).performClick()
        assertEquals(MainScaffoldUiAction.OpenRegisterSheet, actions.single())
    }

    @Test
    fun sheetListsActionsAndChoosingDispatches() {
        val actions = mutableListOf<MainScaffoldUiAction>()
        show(
            MainScaffoldUiState(registerActions = listOf(action), registerSheetVisible = true),
            onAction = { actions += it },
        )
        rule.onNodeWithText(string(R.string.more_entry_privacy)).assertIsDisplayed().performClick()
        assertEquals(MainScaffoldUiAction.ChooseRegisterAction(action), actions.single())
    }
}
