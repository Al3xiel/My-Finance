package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.al3xiel.my_finances.feature.diagnostics.domain.usecase.WorkspaceSummary
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DiagnosticWorkspaceContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val summary = WorkspaceSummary(
        exportId = "e1", scope = "FULL", anonymized = true, eventCount = 12, exportedAt = "2026-11-02", appVersion = "1.0",
        androidVersion = "16", alteredEventCount = 3, packageHashPrefix = "abc123", pseudonyms = listOf("P1", "P2"),
    )

    private fun show(state: DiagnosticWorkspaceUiState, onAction: (DiagnosticWorkspaceUiAction) -> Unit = {}) =
        rule.setContent { MyfinancesTheme { DiagnosticWorkspaceContent(state, onAction) } }

    @Test fun showsItsTitle() {
        show(DiagnosticWorkspaceUiState(stage = WorkspaceStage.EMPTY))
        rule.onNodeWithText("Espacio de diagnóstico").assertIsDisplayed()
    }

    @Test fun withoutAPackageItSaysSoAndOffersToOpenOne() {
        val actions = mutableListOf<DiagnosticWorkspaceUiAction>()
        show(DiagnosticWorkspaceUiState(stage = WorkspaceStage.EMPTY)) { actions += it }
        rule.onNodeWithText("No hay ningún paquete abierto. Abre un archivo .fdiag que te hayan enviado.").assertIsDisplayed()
        rule.onNodeWithText("Abrir paquete").performClick()
        assertEquals(DiagnosticWorkspaceUiAction.OpenPackage, actions.single())
    }

    @Test fun theRequestButtonIsBlockedUntilSomethingIsSelectedAndSaysWhy() {
        show(DiagnosticWorkspaceUiState(stage = WorkspaceStage.READY, summary = summary))
        rule.onNodeWithText("Marca al menos un seudónimo para generar la solicitud.").assertIsDisplayed()
        rule.onNodeWithText("Generar solicitud").assertIsNotEnabled()
    }

    @Test fun withASelectionTheRequestCanBeGenerated() {
        show(DiagnosticWorkspaceUiState(stage = WorkspaceStage.READY, summary = summary, requestSelection = setOf("P1")))
        rule.onNodeWithText("Marca al menos un seudónimo para generar la solicitud.").assertDoesNotExist()
        rule.onNodeWithText("Generar solicitud").assertIsEnabled()
    }
}
