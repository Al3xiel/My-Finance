package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.feature.privacy.domain.model.ThemeMode
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        SettingsContent(state = state, onAction = viewModel::onAction)
        viewModel.sections.forEach { section -> key(section.id) { section.content() } }
    }
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
    /** El color del sistema existe desde Android 12; antes no se ofrece. */
    dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = state.themeMode == mode,
                            onClick = { onAction(SettingsUiAction.SetThemeMode(mode)) },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.themeMode == mode, onClick = null)
                    Text(stringResource(mode.labelRes()), modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                }
            }
        }
        if (dynamicColorSupported) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.dynamicColor,
                        onValueChange = { onAction(SettingsUiAction.SetDynamicColor(it)) },
                        role = Role.Switch,
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_dynamic_color), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.settings_dynamic_color_hint), style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = state.dynamicColor, onCheckedChange = null)
            }
        }
        Text(stringResource(R.string.settings_currency), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_currency_value))
        Text(stringResource(R.string.settings_format), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.settings_format_value))
    }
}

private fun ThemeMode.labelRes() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.AMOLED -> R.string.theme_amoled
}

@Preview(showBackground = true)
@Composable
private fun SettingsContentPreview() {
    MyfinancesTheme { SettingsContent(SettingsUiState(ThemeMode.AMOLED, dynamicColor = true), onAction = {}, dynamicColorSupported = true) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsContentDarkPreview() {
    MyfinancesTheme { SettingsContent(SettingsUiState(), onAction = {}) }
}
