package com.al3xiel.my_finances.feature.movements.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IncomeEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun accountAndSourceAreChipsAndTappingSelects() {
        val actions = mutableListOf<IncomeEditorUiAction>()
        val state = IncomeEditorUiState(
            loaded = true,
            amountText = "1500",
            accountId = AccountId("a"),
            sourceId = IncomeSourceId("s"),
            accountOptions = listOf(EntityOption("a", "BBVA"), EntityOption("b", "Efectivo")),
            sourceOptions = listOf(EntityOption("s", "Sueldo"), EntityOption("t", "Venta")),
        )
        rule.setContent { MyfinancesTheme { IncomeEditorContent(state, actions::add) } }
        rule.onNodeWithText("Efectivo").assertIsDisplayed().performClick()
        rule.onNodeWithText("Venta").performClick()
        assertEquals(
            listOf<IncomeEditorUiAction>(
                IncomeEditorUiAction.SelectAccount(AccountId("b")),
                IncomeEditorUiAction.SelectSource(IncomeSourceId("t")),
            ),
            actions,
        )
    }

    @Test
    fun withoutSourcesTheEditorSaysSoAndWhereToCreateOne() {
        val state = IncomeEditorUiState(
            loaded = true,
            accountId = AccountId("a"),
            accountOptions = listOf(EntityOption("a", "BBVA")),
            sourceOptions = emptyList(),
        )
        rule.setContent { MyfinancesTheme { IncomeEditorContent(state, {}) } }
        rule.onNodeWithText("Aún no tienes fuentes de ingreso. Créala en Más → Fuentes de ingreso.").assertIsDisplayed()
    }

    @Test
    fun withSourcesNoEmptyHintIsShown() {
        val state = IncomeEditorUiState(
            loaded = true,
            accountId = AccountId("a"),
            sourceId = IncomeSourceId("s"),
            accountOptions = listOf(EntityOption("a", "BBVA")),
            sourceOptions = listOf(EntityOption("s", "Sueldo")),
        )
        rule.setContent { MyfinancesTheme { IncomeEditorContent(state, {}) } }
        rule.onNodeWithText("Aún no tienes fuentes de ingreso. Créala en Más → Fuentes de ingreso.").assertDoesNotExist()
    }
}
