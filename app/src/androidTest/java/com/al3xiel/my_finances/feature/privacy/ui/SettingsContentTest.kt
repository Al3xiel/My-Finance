package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(
        state: SettingsUiState = SettingsUiState(),
        supported: Boolean = true,
        onAction: (SettingsUiAction) -> Unit = {},
    ) = rule.setContent {
        MyfinancesTheme {
            SettingsContent(state, onAction, dynamicColorSupported = supported, sections = { Text("Recordatorios de prueba") })
        }
    }

    @Test fun showsItsTitleAndTheSectionsOfOtherFeaturesBelow() {
        show()
        rule.onNodeWithText("Ajustes").assertIsDisplayed()
        rule.onNodeWithText("Recordatorios de prueba").assertIsDisplayed()
    }

    @Test fun choosingAThemeDispatchesIt() {
        val actions = mutableListOf<SettingsUiAction>()
        show(onAction = { actions += it })
        rule.onNodeWithText("Oscuro puro").performClick()
        assertEquals(SettingsUiAction.SetThemeMode(ThemeMode.AMOLED), actions.single())
    }

    @Test fun theSystemColorSwitchIsOnlyOfferedWhereItExists() {
        show(supported = false)
        rule.onNodeWithText("Color del sistema").assertDoesNotExist()
    }

    @Test fun theSystemColorSwitchTogglesWhenSupported() {
        val actions = mutableListOf<SettingsUiAction>()
        show(onAction = { actions += it })
        rule.onNodeWithText("Color del sistema").performClick()
        assertEquals(SettingsUiAction.SetDynamicColor(true), actions.single())
    }

    @Test fun currencyAndFormatAreInformationOnly() {
        show()
        rule.onNodeWithText("Soles (PEN)").assertIsDisplayed()
        rule.onNodeWithText("es-PE").assertIsDisplayed()
    }
}
