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

class MoreScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val entries = listOf(
        MoreMenuEntry("privacy", R.string.more_entry_privacy, Privacy, order = 1),
        MoreMenuEntry("settings", R.string.more_entry_settings, Settings, order = 2),
    )

    @Test
    fun listsTheContributedEntries() {
        rule.setContent { MoreContent(entries, onOpen = {}) }
        rule.onNodeWithText(string(R.string.more_entry_privacy)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.more_entry_settings)).assertIsDisplayed()
    }

    @Test
    fun tappingAnEntryOpensItsRoute() {
        val opened = mutableListOf<Route>()
        rule.setContent { MoreContent(entries, onOpen = { opened += it }) }
        rule.onNodeWithText(string(R.string.more_entry_settings)).performClick()
        assertEquals(listOf<Route>(Settings), opened)
    }

    @Test
    fun withoutEntriesShowsTheEmptyState() {
        rule.setContent { MoreContent(emptyList(), onOpen = {}) }
        rule.onNodeWithText(string(R.string.empty_state_title)).assertIsDisplayed()
    }

    @Test
    fun theMoreTabShowsTheEntriesOfTheState() {
        val state = MainScaffoldUiState(
            stacks = TabBackStacks().select(MainTab.MORE),
            moreEntries = entries,
        )
        rule.setContent { MainScaffoldContent(state, {}, rememberRouteEntryProvider(emptySet())) }
        rule.onNodeWithText(string(R.string.more_entry_privacy)).assertIsDisplayed()
    }
}
