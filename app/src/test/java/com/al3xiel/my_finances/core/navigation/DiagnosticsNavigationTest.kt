package com.al3xiel.my_finances.core.navigation

import androidx.navigation3.runtime.entryProvider
import com.al3xiel.my_finances.feature.diagnostics.di.DiagnosticsNavigationModule
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DiagnosticsNavigationTest {
    private val routes = DiagnosticsNavigationModule.routes()

    private val provider = entryProvider<Route>(fallback = { error("no screen for $it") }) {
        with(routes) { register() }
    }

    @Test
    fun contributesTheScreenOfEachOfItsRoutes() {
        provider(DevTools)
        provider(DiagnosticExport())
        provider(DiagnosticExport("r1"))
        provider(DiagnosticWorkspace)
        provider(DictionaryExport("r1"))
    }

    @Test
    fun doesNotContributeScreensOfOtherFeatures() {
        assertThrows(IllegalStateException::class.java) { provider(Backup) }
        assertThrows(IllegalStateException::class.java) { provider(ReconciliationDetail("r1")) }
    }

    @Test
    fun theRoutesSurviveSavingAndRestoringTheTabState() {
        val state = TabBackStacks().select(MainTab.MORE).push(DiagnosticExport("r1")).push(DictionaryExport("r1")).push(DiagnosticWorkspace)
        val restored = Json.decodeFromString(TabBackStacks.serializer(), Json.encodeToString(TabBackStacks.serializer(), state))
        assertEquals(state, restored)
        assertEquals(listOf(More, DiagnosticExport("r1"), DictionaryExport("r1"), DiagnosticWorkspace), restored.activeStack)
    }
}
