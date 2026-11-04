package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Rule
import org.junit.Test

class DictionaryExportContentTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: DictionaryExportUiState) = rule.setContent { MyfinancesTheme { DictionaryExportContent(state, {}, {}) } }

    @Test fun showsItsTitle() {
        show(DictionaryExportUiState())
        rule.onNodeWithText("Exportar diccionario").assertIsDisplayed()
    }

    @Test fun withoutAValidCodeTheButtonIsBlockedAndSaysWhy() {
        show(DictionaryExportUiState(preview = null))
        rule.onNodeWithText("Pega un código de solicitud válido para ver qué se revelará.").assertIsDisplayed()
        rule.onNodeWithText("Exportar").assertIsNotEnabled()
    }

    @Test fun withAPreviewItIsEnabled() {
        show(DictionaryExportUiState(preview = DictionaryPreview(listOf("P1"), emptyList(), 0)))
        rule.onNodeWithText("Pega un código de solicitud válido para ver qué se revelará.").assertDoesNotExist()
        rule.onNodeWithText("Exportar").assertIsEnabled()
    }
}
