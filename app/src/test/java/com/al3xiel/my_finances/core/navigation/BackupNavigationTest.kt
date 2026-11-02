package com.al3xiel.my_finances.core.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.navigation3.runtime.entryProvider
import com.al3xiel.my_finances.feature.backup.di.BackupNavigationModule
import com.al3xiel.my_finances.feature.privacy.di.PrivacyNavigationModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupNavigationTest {
    private val routes = BackupNavigationModule.routes()
    private val moreMenu = BackupNavigationModule.moreMenu()

    @Test
    fun moreOffersBackupBetweenTheCatalogsAndPrivacy() {
        val registry = MoreMenuRegistry(setOf(moreMenu, PrivacyNavigationModule.moreMenu()))
        val ids = registry.entries.map { it.id }

        assertEquals(Backup, registry.entries.single { it.id == "backup" }.route)
        assertTrue(ids.indexOf("backup") < ids.indexOf("privacy"))
    }

    @Test
    fun backupContributesTheScreenOfItsOwnRouteOnly() {
        val provider = entryProvider<Route>(fallback = { error("no screen for $it") }) {
            with(routes) { register() }
        }
        provider(Backup)
        assertThrows(IllegalStateException::class.java) { provider(Reports) }
    }

    @Test
    fun openingTheEntryPutsBackupOnTopOfTheMoreTab() {
        val viewModel = MainScaffoldViewModel(
            SavedStateHandle(),
            RegisterActionRegistry(emptySet()),
            MoreMenuRegistry(setOf(moreMenu)),
            setOf(routes),
        )
        viewModel.onAction(MainScaffoldUiAction.SelectTab(MainTab.MORE))
        viewModel.onAction(MainScaffoldUiAction.Push(viewModel.uiState.value.moreEntries.first().route))

        assertEquals(listOf(More, Backup), viewModel.uiState.value.stacks.activeStack)
    }

    @Test
    fun theBackupRouteSurvivesSavingAndRestoringTheTabState() {
        val state = TabBackStacks().select(MainTab.MORE).push(Backup)
        val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))

        assertEquals(state, restored)
        assertEquals(listOf(More, Backup), restored.activeStack)
    }
}
