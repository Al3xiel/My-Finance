package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
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
    SettingsContent(
        state = state,
        onAction = viewModel::onAction,
        modifier = modifier,
        sections = { viewModel.sections.forEach { section -> key(section.id) { section.content() } } },
    )
}

/** Ajustes: tema, color del sistema, moneda y formato; debajo, las secciones que aportan otras features. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onAction: (SettingsUiAction) -> Unit,
    modifier: Modifier = Modifier,
    /** El color del sistema existe desde Android 12; antes no se ofrece. */
    dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    sections: @Composable ColumnScope.() -> Unit = {},
) {
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.more_entry_settings), modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SectionCard(title = stringResource(R.string.settings_theme)) {
                Column(modifier = Modifier.selectableGroup()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) SectionDivider()
                        SectionRow(
                            headline = stringResource(mode.labelRes()),
                            modifier = Modifier.selectable(
                                selected = state.themeMode == mode,
                                onClick = { onAction(SettingsUiAction.SetThemeMode(mode)) },
                                role = Role.RadioButton,
                            ),
                            leading = { RadioButton(selected = state.themeMode == mode, onClick = null) },
                        )
                    }
                }
            }
            if (dynamicColorSupported) {
                SectionCard {
                    SectionRow(
                        headline = stringResource(R.string.settings_dynamic_color),
                        supporting = stringResource(R.string.settings_dynamic_color_hint),
                        modifier = Modifier.toggleable(
                            value = state.dynamicColor,
                            onValueChange = { onAction(SettingsUiAction.SetDynamicColor(it)) },
                            role = Role.Switch,
                        ),
                        trailing = { Switch(checked = state.dynamicColor, onCheckedChange = null) },
                    )
                }
            }
            SectionCard {
                SectionRow(headline = stringResource(R.string.settings_currency), trailing = { Text(stringResource(R.string.settings_currency_value)) })
                SectionDivider()
                SectionRow(headline = stringResource(R.string.settings_format), trailing = { Text(stringResource(R.string.settings_format_value)) })
            }
            sections()
        }
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
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun SettingsContentDarkPreview() {
    MyfinancesTheme { SettingsContent(SettingsUiState(), onAction = {}) }
}
