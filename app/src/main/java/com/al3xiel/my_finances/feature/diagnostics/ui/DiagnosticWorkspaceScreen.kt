package com.al3xiel.my_finances.feature.diagnostics.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.security.AuthPrompt

/** S64: lanza los selectores del sistema (`OpenDocument`) y re-autentica al entrar; el resto vive en [DiagnosticWorkspaceContent]. */
@Composable
fun DiagnosticWorkspaceScreen(modifier: Modifier = Modifier, viewModel: DiagnosticWorkspaceViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prompt = AuthPrompt(stringResource(R.string.diagnostic_workspace_auth_title), stringResource(R.string.diagnostic_workspace_auth_subtitle))

    val packageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onAction(DiagnosticWorkspaceUiAction.PackagePicked(uri?.toString()))
    }
    val dictionaryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        viewModel.onAction(DiagnosticWorkspaceUiAction.DictionaryPicked(uri?.toString()))
    }

    LaunchedEffect(Unit) { viewModel.onAction(DiagnosticWorkspaceUiAction.Authenticate(prompt)) }
    LaunchedEffect(state.stage) {
        when (state.stage) {
            WorkspaceStage.PICKING_PACKAGE -> {
                viewModel.onAction(DiagnosticWorkspaceUiAction.PickerOpened)
                packageLauncher.launch(arrayOf("*/*"))
            }
            WorkspaceStage.PICKING_DICTIONARY -> {
                viewModel.onAction(DiagnosticWorkspaceUiAction.PickerOpened)
                dictionaryLauncher.launch(arrayOf("*/*"))
            }
            else -> Unit
        }
    }
    DiagnosticWorkspaceContent(state, viewModel::onAction, modifier)
}
