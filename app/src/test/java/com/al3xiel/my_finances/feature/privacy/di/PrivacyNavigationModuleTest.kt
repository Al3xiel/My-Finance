package com.al3xiel.my_finances.feature.privacy.di

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.entryProvider
import com.al3xiel.my_finances.core.navigation.MainScaffoldUiAction
import com.al3xiel.my_finances.core.navigation.MainScaffoldViewModel
import com.al3xiel.my_finances.core.navigation.MainTab
import com.al3xiel.my_finances.core.navigation.More
import com.al3xiel.my_finances.core.navigation.MoreMenuRegistry
import com.al3xiel.my_finances.core.navigation.Privacy
import com.al3xiel.my_finances.core.navigation.RegisterActionRegistry
import com.al3xiel.my_finances.core.navigation.Reports
import com.al3xiel.my_finances.core.navigation.Route
import com.al3xiel.my_finances.core.navigation.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PrivacyNavigationModuleTest {

    private val routes = PrivacyNavigationModule.routes()
    private val moreMenu = PrivacyNavigationModule.moreMenu()

    @Test
    fun `the more menu offers Privacy then Settings`() {
        val entries = MoreMenuRegistry(setOf(moreMenu)).entries
        assertEquals(listOf(Privacy, Settings), entries.map { it.route })
    }

    @Test
    fun `privacy contributes the screens of its two routes only`() {
        val provider = entryProvider<Route>(fallback = { error("no screen for $it") }) {
            with(routes) { register() }
        }
        provider(Privacy)
        provider(Settings)
        assertThrows(IllegalStateException::class.java) { provider(Reports) }
    }

    @Test
    fun `opening a more entry puts its route on top of the More tab`() {
        val viewModel = MainScaffoldViewModel(
            SavedStateHandle(),
            RegisterActionRegistry(emptySet()),
            MoreMenuRegistry(setOf(moreMenu)),
            setOf(routes),
        )
        viewModel.onAction(MainScaffoldUiAction.SelectTab(MainTab.MORE))
        val entry = viewModel.uiState.value.moreEntries.first()
        viewModel.onAction(MainScaffoldUiAction.Push(entry.route))
        assertEquals(listOf(More, Privacy), viewModel.uiState.value.stacks.activeStack)
    }
}
