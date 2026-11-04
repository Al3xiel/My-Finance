package com.al3xiel.my_finances.core.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/**
 * Cada pantalla de la navegación tiene su propio ViewModel: al salir de ella se descarta y al volver a abrirla empieza de cero.
 * Sin esto un editor que ya guardó (`saved = true`) se cerraba solo la siguiente vez que se abría.
 */
class NavigationViewModelScopeTest {
    @get:Rule
    val rule = createComposeRule()

    class ProbeViewModel : ViewModel()

    private val seen = mutableListOf<ProbeViewModel>()

    private val entryProvider: (Route) -> NavEntry<Route> = { route ->
        NavEntry(route) {
            if (route == Privacy) {
                val vm = viewModel<ProbeViewModel>()
                SideEffect { if (seen.lastOrNull() !== vm) seen += vm }
                Text("probe")
            } else {
                Text("root")
            }
        }
    }

    private var state by mutableStateOf(MainScaffoldUiState())

    private fun open() {
        rule.runOnIdle { state = state.copy(stacks = state.stacks.push(Privacy)) }
        rule.waitForIdle()
    }

    private fun close() {
        rule.runOnIdle { state = state.copy(stacks = state.stacks.back() ?: state.stacks) }
        rule.waitForIdle()
    }

    @Test
    fun aScreenOpenedAgainStartsWithAFreshViewModel() {
        rule.setContent { MainScaffoldContent(state, {}, entryProvider) }

        open()
        close()
        open()

        assertEquals(2, seen.size)
        assertNotSame(seen[0], seen[1])
    }

    @Test
    fun theViewModelSurvivesWhileTheScreenStaysOpen() {
        rule.setContent { MainScaffoldContent(state, {}, entryProvider) }

        open()
        rule.runOnIdle { state = state.copy(registerSheetVisible = true) }
        rule.waitForIdle()

        assertEquals(1, seen.size)
        assertSame(seen[0], seen.last())
    }
}
