package com.al3xiel.my_finances.feature.expectedincome.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

/** La sección de recordatorios de ingresos esperados dentro de Ajustes (S72); la monta `SettingsSectionContributor`. */
@Composable
fun ReminderSettingsScreen(modifier: Modifier = Modifier, viewModel: ReminderSettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAction(ReminderSettingsUiAction.PermissionResult(it))
    }
    LaunchedEffect(state.requestPermission) {
        if (state.requestPermission) {
            viewModel.onAction(ReminderSettingsUiAction.PermissionRequestLaunched)
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    ReminderSettingsContent(
        state = state,
        onAction = viewModel::onAction,
        hasPermission = {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        },
        modifier = modifier,
    )
}

@Composable
fun ReminderSettingsContent(
    state: ReminderSettingsUiState,
    onAction: (ReminderSettingsUiAction) -> Unit,
    hasPermission: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    SectionCard(title = stringResource(R.string.settings_reminders), modifier = modifier) {
        SectionRow(
            headline = stringResource(R.string.settings_reminders_switch),
            supporting = stringResource(R.string.settings_reminders_hint),
            trailing = {
                Switch(checked = state.enabled, onCheckedChange = { onAction(ReminderSettingsUiAction.Toggle(it, hasPermission())) })
            },
        )
        if (state.permissionDenied) {
            SectionDivider()
            SectionRow(headline = stringResource(R.string.settings_reminders_denied))
        }
    }
    if (state.showExplanation) {
        AlertDialog(
            onDismissRequest = { onAction(ReminderSettingsUiAction.DismissExplanation) },
            title = { Text(stringResource(R.string.settings_reminders_permission_title)) },
            text = { Text(stringResource(R.string.settings_reminders_permission_body)) },
            confirmButton = {
                TextButton(onClick = { onAction(ReminderSettingsUiAction.AcceptExplanation) }) {
                    Text(stringResource(R.string.settings_reminders_permission_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(ReminderSettingsUiAction.DismissExplanation) }) {
                    Text(stringResource(R.string.settings_reminders_permission_later))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun ReminderSettingsContentPreview() {
    MyfinancesTheme { ReminderSettingsContent(ReminderSettingsUiState(enabled = true), onAction = {}, hasPermission = { true }) }
}
