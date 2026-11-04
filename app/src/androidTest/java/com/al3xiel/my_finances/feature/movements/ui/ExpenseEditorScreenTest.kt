package com.al3xiel.my_finances.feature.movements.ui

import com.al3xiel.my_finances.core.designsystem.LocalHideAmounts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExpenseEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val state = ExpenseEditorUiState(
        loaded = true,
        totalText = "10",
        accountId = AccountId("a"),
        parts = listOf(PartDraft(0, "10", CategoryId("c1"), setOf(TagId("t1")))),
        accountOptions = listOf(EntityOption("a", "BBVA"), EntityOption("b", "Efectivo")),
        categoryOptions = listOf(EntityOption("c1", "Comida"), EntityOption("c2", "Hogar")),
        tagOptions = listOf(EntityOption("t1", "Viaje")),
    )

    private fun show(onAction: (ExpenseEditorUiAction) -> Unit = {}) =
        rule.setContent { MyfinancesTheme { ExpenseEditorContent(state, onAction) } }

    @Test
    fun accountAndCategoryOptionsAreVisibleChipsAndTappingOneSelectsIt() {
        val actions = mutableListOf<ExpenseEditorUiAction>()
        show(actions::add)
        rule.onNodeWithText("BBVA").assertIsDisplayed()
        rule.onNodeWithText("Hogar").assertIsDisplayed().performClick()
        rule.onNodeWithText("Efectivo").performClick()
        assertEquals(
            listOf<ExpenseEditorUiAction>(
                ExpenseEditorUiAction.SelectPartCategory(0, CategoryId("c2")),
                ExpenseEditorUiAction.SelectAccount(AccountId("b")),
            ),
            actions,
        )
    }

    @Test
    fun theSaveFooterIsAlwaysVisibleAndSaves() {
        val actions = mutableListOf<ExpenseEditorUiAction>()
        show(actions::add)
        rule.onNodeWithText(string(R.string.editor_save)).assertIsDisplayed().performClick()
        assertEquals(listOf<ExpenseEditorUiAction>(ExpenseEditorUiAction.Save), actions)
    }

    private fun withParts(vararg amounts: String) = state.copy(
        totalText = if (amounts.size > 1) amounts.sumOf { it.toLong() }.toString() + ".00" else amounts.single(),
        parts = amounts.mapIndexed { i, amount -> PartDraft(i, amount, CategoryId("c1")) },
    )

    @Test
    fun eachPartHasAnOptionalDetailThatDispatchesWhatIsTyped() {
        val actions = mutableListOf<ExpenseEditorUiAction>()
        rule.setContent { MyfinancesTheme { ExpenseEditorContent(withParts("10"), actions::add) } }
        rule.onNodeWithText("Detalle (opcional)").performTextInput("Pastillas")
        assertEquals(listOf<ExpenseEditorUiAction>(ExpenseEditorUiAction.EditPartNote(0, "Pastillas")), actions)
    }

    @Test
    fun severalPartsEachGetTheirOwnDetailField() {
        rule.setContent { MyfinancesTheme { ExpenseEditorContent(withParts("6", "4"), {}) } }
        assertEquals(2, rule.onAllNodesWithText("Detalle (opcional)").fetchSemanticsNodes().size)
    }

    @Test
    fun withSeveralPartsTheTotalIsTheirSumAndHasNoField() {
        rule.setContent {
            CompositionLocalProvider(LocalHideAmounts provides false) { MyfinancesTheme { ExpenseEditorContent(withParts("6", "4"), {}) } }
        }
        rule.onNodeWithText("Total (suma de las partes)").assertIsDisplayed()
        rule.onNodeWithText("S/ 10.00").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.editor_total)).assertDoesNotExist()
    }

    @Test
    fun withOnePartTheTotalIsStillTypedDirectly() {
        rule.setContent { MyfinancesTheme { ExpenseEditorContent(withParts("10"), {}) } }
        rule.onNodeWithText(string(R.string.editor_total)).assertIsDisplayed()
        rule.onNodeWithText("Total (suma de las partes)").assertDoesNotExist()
    }

    @Test
    fun theCalculatedTotalRespectsHiddenAmounts() {
        rule.setContent {
            CompositionLocalProvider(LocalHideAmounts provides true) { MyfinancesTheme { ExpenseEditorContent(withParts("6", "4"), {}) } }
        }
        rule.onNodeWithText("S/ 10.00").assertDoesNotExist()
    }
}
