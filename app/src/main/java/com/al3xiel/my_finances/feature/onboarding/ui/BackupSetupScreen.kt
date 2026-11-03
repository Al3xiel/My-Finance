package com.al3xiel.my_finances.feature.onboarding.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

private val INTERVAL_OPTIONS = listOf(7, 15, 30)

/** S05: explica el respaldo, ofrece el recordatorio (permiso en contexto) y enlaza a S70; «Terminar» entra a la app. */
@Composable
fun BackupSetupScreen(
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onAction(BackupSetupUiAction.PermissionResult(it))
    }
    LaunchedEffect(state.requestPermission) {
        if (state.requestPermission) {
            viewModel.onAction(BackupSetupUiAction.PermissionRequestLaunched)
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    BackupSetupContent(
        state = state,
        onAction = viewModel::onAction,
        onOpenBackup = onOpenBackup,
        hasPermission = {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        },
        modifier = modifier,
    )
}

@Composable
fun BackupSetupContent(
    state: BackupSetupUiState,
    onAction: (BackupSetupUiAction) -> Unit,
    onOpenBackup: () -> Unit,
    hasPermission: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.onboarding_backup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.backup_reminder_switch), modifier = Modifier.weight(1f))
            Switch(
                checked = state.reminderEnabled,
                onCheckedChange = { onAction(BackupSetupUiAction.ToggleReminder(it, hasPermission())) },
            )
        }
        if (state.permissionDenied) {
            Text(stringResource(R.string.backup_reminder_denied), color = MaterialTheme.colorScheme.error)
        }
        if (state.reminderEnabled) {
            Text(stringResource(R.string.backup_reminder_interval))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                INTERVAL_OPTIONS.forEach { days ->
                    FilterChip(
                        selected = state.intervalDays == days,
                        onClick = { onAction(BackupSetupUiAction.SetIntervalDays(days)) },
                        label = { Text(pluralStringResource(R.plurals.backup_reminder_days, days, days)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        TextButton(onClick = onOpenBackup) { Text(stringResource(R.string.onboarding_backup_open)) }
        Button(
            onClick = { onAction(BackupSetupUiAction.Finish) },
            enabled = !state.finished,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_finish))
        }
    }

    if (state.showExplanation) {
        AlertDialog(
            onDismissRequest = { onAction(BackupSetupUiAction.DismissExplanation) },
            title = { Text(stringResource(R.string.backup_reminder_explanation_title)) },
            text = { Text(stringResource(R.string.backup_reminder_explanation)) },
            confirmButton = {
                TextButton(onClick = { onAction(BackupSetupUiAction.AcceptExplanation) }) {
                    Text(stringResource(R.string.backup_reminder_explanation_ok))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackupSetupContentPreview() {
    MyfinancesTheme {
        BackupSetupContent(
            BackupSetupUiState(reminderEnabled = true),
            onAction = {},
            onOpenBackup = {},
            hasPermission = { true },
        )
    }
}
