package com.al3xiel.my_finances.feature.pools.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PoolEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theResponsiblePersonIsAChipAndTappingSelectsIt() {
        val actions = mutableListOf<PoolEditorUiAction>()
        val state = PoolEditorUiState(
            loaded = true,
            name = "Perros",
            personOptions = listOf(EntityOption("mom", "Mamá"), EntityOption("dad", "Papá")),
        )
        rule.setContent { MyfinancesTheme { PoolEditorContent(state, actions::add) } }
        rule.onNodeWithText("Papá").assertIsDisplayed().performClick()
        assertEquals(listOf<PoolEditorUiAction>(PoolEditorUiAction.SelectPerson(PersonId("dad"))), actions)
    }
}
