package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class DiagnosticExportContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val preview = ExportPreview(42, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 2), listOf("a", "b"))

    private fun show(state: DiagnosticExportUiState, onFinished: () -> Unit = {}) =
        rule.setContent { MyfinancesTheme { DiagnosticExportContent(state, {}, onFinished) } }

    @Test fun showsItsTitle() {
        show(DiagnosticExportUiState())
        rule.onNodeWithText("Exportar diagnóstico").assertIsDisplayed()
    }

    @Test fun withoutAPreviewTheButtonIsBlockedAndSaysWhy() {
        show(DiagnosticExportUiState(preview = null))
        rule.onNodeWithText("Preparando la vista previa de lo que se exportará…").assertIsDisplayed()
        rule.onNodeWithText("Exportar").assertIsNotEnabled()
    }

    @Test fun withAPreviewItIsEnabledAndShowsWhatWillBeExported() {
        show(DiagnosticExportUiState(preview = preview))
        rule.onNodeWithText("Preparando la vista previa de lo que se exportará…").assertDoesNotExist()
        rule.onNodeWithText("Exportar").assertIsEnabled()
        rule.onNodeWithText("42 eventos").assertIsDisplayed()
    }

    @Test fun exportingWithoutAnonymizingWarnsThatNamesTravel() {
        show(DiagnosticExportUiState(preview = preview, anonymize = false))
        rule.onNodeWithText("Sin anonimizar, los nombres y las notas viajan en el archivo.").assertIsDisplayed()
    }

    @Test fun whenDoneItOffersToFinish() {
        var finished = 0
        show(DiagnosticExportUiState(stage = ExportStage.DONE)) { finished++ }
        rule.onNodeWithText("Diagnóstico guardado").assertIsDisplayed()
        rule.onNodeWithText("Listo").performClick()
        assertEquals(1, finished)
    }
}
