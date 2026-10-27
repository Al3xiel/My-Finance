package com.al3xiel.my_finances.core.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.al3xiel.my_finances.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainScaffoldTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(
        state: MainScaffoldUiState = MainScaffoldUiState(),
        onAction: (MainScaffoldUiAction) -> Unit = {},
    ) = rule.setContent {
        MainScaffoldContent(state, onAction, rememberRouteEntryProvider(emptySet()))
    }

    @Test
    fun showsTheFiveTabs() {
        show()
        listOf(
            R.string.tab_home,
            R.string.tab_movements,
            R.string.tab_receivables,
            R.string.tab_reports,
            R.string.tab_more,
        ).forEach { rule.onNodeWithText(string(it)).assertIsDisplayed() }
    }

    @Test
    fun tappingATabDispatchesSelectTab() {
        val actions = mutableListOf<MainScaffoldUiAction>()
        show(onAction = { actions += it })
        rule.onNodeWithText(string(R.string.tab_reports)).performClick()
        assertEquals(MainScaffoldUiAction.SelectTab(MainTab.REPORTS), actions.single())
    }

    @Test
    fun aRootWithoutContributionShowsThePlaceholder() {
        show()
        rule.onNodeWithText(string(R.string.empty_state_title)).assertIsDisplayed()
    }
}
