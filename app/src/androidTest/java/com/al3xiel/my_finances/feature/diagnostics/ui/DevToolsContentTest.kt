package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DevToolsContentTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: DevToolsUiState, onAction: (DevToolsUiAction) -> Unit = {}) =
        rule.setContent { MyfinancesTheme { DevToolsContent(state, onAction, onOpenPackage = {}, prompt = AuthPrompt("t", "s")) } }

    @Test fun showsItsTitle() {
        show(DevToolsUiState())
        rule.onNodeWithText("Herramientas de desarrollador").assertIsDisplayed()
    }

    @Test fun lockedItAsksToConfirmIdentityBeforeAnythingElse() {
        val actions = mutableListOf<DevToolsUiAction>()
        show(DevToolsUiState(stage = DevToolsStage.LOCKED)) { actions += it }
        rule.onNodeWithText("Verificar cadena de eventos").assertDoesNotExist()
        rule.onNodeWithText("Confirmar identidad").performClick()
        assertTrue(actions.single() is DevToolsUiAction.Authenticate)
    }

    @Test fun readyItOffersTheToolsAndSaysWhenThereAreNoEvents() {
        show(DevToolsUiState(stage = DevToolsStage.READY))
        rule.onNodeWithText("Verificar cadena de eventos").assertIsEnabled()
        rule.onNodeWithText("Aún no hay eventos registrados.").assertIsDisplayed()
    }

    @Test fun whileWorkingTheToolsAreBlockedAndSaySo() {
        show(DevToolsUiState(stage = DevToolsStage.WORKING))
        rule.onNodeWithText("Verificar cadena de eventos").assertIsNotEnabled()
        rule.onNodeWithText("Trabajando…").assertIsDisplayed()
    }
}
