package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.about_version, state.version),
            modifier = Modifier.fillMaxWidth().clickable { onAction(AboutUiAction.VersionTapped) },
        )
        state.tapsRemaining?.let { Text(pluralStringResource(R.plurals.about_taps_remaining, it, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.devToolsEnabled) {
            OutlinedButton(onClick = onOpenDevTools, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_open_devtools)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AboutContentPreview() {
    MyfinancesTheme { AboutContent(AboutUiState("1.0", devToolsEnabled = true), {}, {}) }
}
