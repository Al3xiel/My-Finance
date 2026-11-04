package com.al3xiel.my_finances.feature.privacy.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.PillButton
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.navigation.Backup
import com.al3xiel.my_finances.core.navigation.LocalNavigator
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme
import kotlin.system.exitProcess

@Composable
fun PrivacyScreen(modifier: Modifier = Modifier, viewModel: PrivacyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navigator = LocalNavigator.current

    // El usuario puede registrar una credencial en Ajustes del sistema y volver.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAction(PrivacyUiAction.RefreshAvailability)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.wipeStage) {
        if (state.wipeStage == WipeStage.DONE) restartApp(context)
    }

    PrivacyContent(
        state = state,
        onAction = viewModel::onAction,
        onBackupFirst = {
            viewModel.onAction(PrivacyUiAction.CancelWipe)
            navigator.push(Backup)
        },
        modifier = modifier,
    )
}

/** Primer uso: tarea nueva y proceso nuevo, para que no quede nada en memoria. */
private fun restartApp(context: Context) {
    context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
    }
    exitProcess(0)
}

@Composable
fun PrivacyContent(
    state: PrivacyUiState,
    onAction: (PrivacyUiAction) -> Unit,
    modifier: Modifier = Modifier,
    onBackupFirst: () -> Unit = {},
) {
    val prompt = AuthPrompt(title = stringResource(R.string.privacy_auth_title))
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.more_entry_privacy), modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SectionCard(title = stringResource(R.string.privacy_what_is_stored_title)) {
                Text(
                    stringResource(R.string.privacy_what_is_stored),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(spacing.lg),
                )
            }
            SectionCard {
                SwitchRow(
                    label = stringResource(R.string.privacy_lock),
                    checked = state.settings.lockEnabled,
                    onCheckedChange = { onAction(PrivacyUiAction.SetLockEnabled(it, prompt)) },
                )
                if (!state.canEnableLock) {
                    SectionDivider()
                    Text(
                        stringResource(R.string.privacy_no_credential),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(spacing.lg),
                    )
                }
                if (state.settings.lockEnabled) {
                    SectionDivider()
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.privacy_auto_lock), style = MaterialTheme.typography.bodyLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            SecuritySettings.AUTO_LOCK_OPTIONS.forEach { minutes ->
                                FilterChip(
                                    selected = state.settings.autoLockMinutes == minutes,
                                    onClick = { onAction(PrivacyUiAction.SetAutoLockMinutes(minutes)) },
                                    label = { Text(autoLockLabel(minutes)) },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                )
                            }
                        }
                    }
                }
            }
            SectionCard {
                SwitchRow(
                    label = stringResource(R.string.privacy_secure_screen),
                    checked = state.settings.secureScreen,
                    onCheckedChange = { onAction(PrivacyUiAction.SetSecureScreen(it)) },
                )
                SectionDivider()
                SwitchRow(
                    label = stringResource(R.string.privacy_hide_amounts),
                    checked = state.settings.hideAmounts,
                    onCheckedChange = { onAction(PrivacyUiAction.SetHideAmounts(it)) },
                )
            }

            state.message?.let { message ->
                Text(stringResource(message.textRes()), color = MaterialTheme.colorScheme.error)
            }

            PillButton(stringResource(R.string.privacy_wipe), { onAction(PrivacyUiAction.StartWipe) }, Modifier.fillMaxWidth(), PillStyle.DANGER)
        }
    }
    WipeDialogs(state = state, prompt = prompt, onAction = onAction, onBackupFirst = onBackupFirst)
}

@Composable
private fun autoLockLabel(minutes: Int) =
    if (minutes == 0) stringResource(R.string.privacy_auto_lock_always)
    else pluralStringResource(R.plurals.privacy_auto_lock_minutes, minutes, minutes)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SectionRow(
        headline = label,
        modifier = Modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun WipeDialogs(
    state: PrivacyUiState,
    prompt: AuthPrompt,
    onAction: (PrivacyUiAction) -> Unit,
    onBackupFirst: () -> Unit,
) {
    when (state.wipeStage) {
        WipeStage.NONE, WipeStage.DONE -> Unit
        WipeStage.WARNING -> AlertDialog(
            onDismissRequest = { onAction(PrivacyUiAction.CancelWipe) },
            title = { Text(stringResource(R.string.privacy_wipe_warning_title)) },
            text = { Text(stringResource(R.string.privacy_wipe_warning)) },
            confirmButton = {
                TextButton(onClick = { onAction(PrivacyUiAction.ContinueWipe(prompt)) }) {
                    Text(stringResource(R.string.privacy_wipe_continue))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onBackupFirst) { Text(stringResource(R.string.privacy_wipe_backup_first)) }
                    CancelButton(onAction)
                }
            },
        )
        WipeStage.CONFIRM_PHRASE -> AlertDialog(
            onDismissRequest = { onAction(PrivacyUiAction.CancelWipe) },
            title = { Text(stringResource(R.string.privacy_wipe_phrase_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.privacy_wipe_phrase, WIPE_CONFIRMATION_PHRASE))
                    val matches = state.typedPhrase.trim().equals(WIPE_CONFIRMATION_PHRASE, ignoreCase = true)
                    OutlinedTextField(
                        value = state.typedPhrase,
                        onValueChange = { onAction(PrivacyUiAction.PhraseChanged(it)) },
                        singleLine = true,
                        supportingText = if (matches) null else ({ Text(stringResource(R.string.privacy_wipe_phrase_hint)) }),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAction(PrivacyUiAction.ConfirmWipe) },
                    enabled = state.typedPhrase.trim().equals(WIPE_CONFIRMATION_PHRASE, ignoreCase = true),
                ) { Text(stringResource(R.string.privacy_wipe_confirm)) }
            },
            dismissButton = { CancelButton(onAction) },
        )
        WipeStage.WIPING -> AlertDialog(
            onDismissRequest = {},
            text = { Text(stringResource(R.string.privacy_wipe_running)) },
            confirmButton = {},
        )
        WipeStage.INCOMPLETE -> AlertDialog(
            onDismissRequest = { onAction(PrivacyUiAction.CancelWipe) },
            title = { Text(stringResource(R.string.privacy_wipe_incomplete_title)) },
            text = { Text(stringResource(R.string.privacy_wipe_incomplete)) },
            confirmButton = {
                TextButton(onClick = { onAction(PrivacyUiAction.CancelWipe) }) { Text(stringResource(R.string.privacy_ok)) }
            },
        )
    }
}

@Composable
private fun CancelButton(onAction: (PrivacyUiAction) -> Unit) {
    TextButton(onClick = { onAction(PrivacyUiAction.CancelWipe) }) { Text(stringResource(R.string.privacy_cancel)) }
}

private fun PrivacyMessage.textRes() = when (this) {
    PrivacyMessage.NO_CREDENTIAL -> R.string.privacy_no_credential
    PrivacyMessage.AUTH_FAILED -> R.string.privacy_auth_failed
}

@Preview(showBackground = true)
@Composable
private fun PrivacyContentPreview() {
    MyfinancesTheme { PrivacyContent(PrivacyUiState(), onAction = {}) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun PrivacyContentDarkPreview() {
    MyfinancesTheme { PrivacyContent(PrivacyUiState(canEnableLock = false, wipeStage = WipeStage.WARNING), onAction = {}) }
}
