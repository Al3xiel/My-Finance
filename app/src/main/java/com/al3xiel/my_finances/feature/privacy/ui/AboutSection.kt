package com.al3xiel.my_finances.feature.privacy.ui

import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionCard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.DevTools
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** Sección "Acerca de" de Ajustes: la versión (siete toques seguidos activan las herramientas) y, ya activadas, su entrada. */
@Composable
fun AboutSection(viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    AboutContent(state, viewModel::onAction, onOpenDevTools = { navigator.push(DevTools) })
}

@Composable
fun AboutContent(state: AboutUiState, onAction: (AboutUiAction) -> Unit, onOpenDevTools: () -> Unit, modifier: Modifier = Modifier) {
    SectionCard(title = stringResource(R.string.about_title), modifier = modifier) {
        SectionRow(
            headline = stringResource(R.string.about_version, state.version),
            onClick = { onAction(AboutUiAction.VersionTapped) },
        )
        state.tapsRemaining?.let {
            SectionDivider()
            SectionRow(headline = pluralStringResource(R.plurals.about_taps_remaining, it, it))
        }
        if (state.devToolsEnabled) {
            SectionDivider()
            SectionRow(headline = stringResource(R.string.about_open_devtools), onClick = onOpenDevTools)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutContentPreview() {
    MyfinancesTheme { AboutContent(AboutUiState("1.0", devToolsEnabled = true), {}, {}) }
}
