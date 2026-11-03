package com.al3xiel.my_finances.feature.quickentry.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.EntityOption
import com.al3xiel.my_finances.feature.quickentry.domain.model.Ambiguity
import com.al3xiel.my_finances.feature.quickentry.domain.model.Candidate
import com.al3xiel.my_finances.feature.quickentry.domain.model.Conflict
import com.al3xiel.my_finances.feature.quickentry.domain.model.EntityKind
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QuickEntryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int, vararg args: Any) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val ready = QuickEntryUiState(
        line = "45 bbva mercado",
        amountText = "45",
        accountId = "a",
        categoryId = "c",
        accountOptions = listOf(EntityOption("a", "BBVA")),
        categoryOptions = listOf(EntityOption("c", "Mercado")),
        canSave = true,
    )

    private fun show(state: QuickEntryUiState, onAction: (QuickEntryUiAction) -> Unit = {}) =
        rule.setContent { MyfinancesTheme { QuickEntryContent(state, onAction) } }

    @Test
    fun withoutALineThereIsNoPreviewAndSaveIsOff() {
        show(QuickEntryUiState())
        rule.onNodeWithText(string(R.string.quick_entry_preview_expense)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.editor_save)).assertIsNotEnabled()
    }

    @Test
    fun anUnderstoodLineShowsThePreviewAndSavesOnTap() {
        val actions = mutableListOf<QuickEntryUiAction>()
        show(ready, actions::add)
        rule.onNodeWithText(string(R.string.quick_entry_preview_expense)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.editor_save)).assertIsEnabled().performClick()
        assertEquals(listOf<QuickEntryUiAction>(QuickEntryUiAction.Save), actions)
    }

    @Test
    fun aSuggestedFieldSaysSoWithText() {
        show(ready.copy(categorySuggested = true))
        rule.onNodeWithText(string(R.string.quick_entry_suggested)).assertIsDisplayed()
    }

    @Test
    fun anAmbiguousTokenIsFlaggedAndOffersEachCandidate() {
        val ambiguity = Ambiguity("mer", listOf(Candidate(EntityKind.CATEGORY, "c", "Mercado"), Candidate(EntityKind.CATEGORY, "d", "Mercería")))
        val actions = mutableListOf<QuickEntryUiAction>()
        show(ready.copy(ambiguities = listOf(ambiguity), canSave = false), actions::add)
        rule.onNodeWithText(string(R.string.quick_entry_ambiguous, "mer")).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.quick_entry_candidate, "Mercería", string(R.string.quick_entry_kind_category))).performClick()
        assertEquals(QuickEntryUiAction.Choose(ambiguity, ambiguity.candidates[1]), actions.single())
        rule.onNodeWithText(string(R.string.editor_save)).assertIsNotEnabled()
    }

    @Test
    fun aConflictIsExplainedAndBlocksSaving() {
        show(ready.copy(conflicts = setOf(Conflict.ACCOUNTS), canSave = false))
        rule.onNodeWithText(string(R.string.quick_entry_conflict_accounts)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.editor_save)).assertIsNotEnabled()
    }

    @Test
    fun accountAndCategoryOptionsAreChipsAndTappingSelects() {
        val actions = mutableListOf<QuickEntryUiAction>()
        show(
            ready.copy(
                accountOptions = listOf(EntityOption("a", "BBVA"), EntityOption("b", "Efectivo")),
                categoryOptions = listOf(EntityOption("c", "Mercado"), EntityOption("d", "Hogar")),
            ),
            actions::add,
        )
        rule.onNodeWithText("Efectivo").assertIsDisplayed().performClick()
        rule.onNodeWithText("Hogar").assertIsDisplayed().performClick()
        assertEquals(listOf(QuickEntryUiAction.SelectAccount("b"), QuickEntryUiAction.SelectCategory("d")), actions)
    }

    @Test
    fun anAmbiguousTokenCarriesAReviewBadgeWithText() {
        val ambiguity = Ambiguity("mer", listOf(Candidate(EntityKind.CATEGORY, "c", "Mercado"), Candidate(EntityKind.CATEGORY, "d", "Mercería")))
        show(ready.copy(ambiguities = listOf(ambiguity), canSave = false))
        rule.onNodeWithText(string(R.string.status_needs_review)).assertIsDisplayed()
    }
}
