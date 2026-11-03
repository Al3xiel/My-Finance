package com.al3xiel.my_finances.feature.backup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.feature.backup.domain.usecase.ChainStatus
import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import com.al3xiel.my_finances.ui.theme.MyfinancesTheme

private val INTERVAL_OPTIONS = listOf(7, 15, 30)

/** S70 sin estado: último respaldo, cadena, crear, restaurar y recordatorio. */
@Composable
fun BackupContent(
    state: BackupUiState,
    onAction: (BackupUiAction) -> Unit,
    hasPermission: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusSection(state, onAction)
        Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium)
        state.message?.let { message ->
            Text(stringResource(message.textRes()), color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = { onAction(BackupUiAction.StartCreate) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.backup_create))
        }
        OutlinedButton(
            onClick = { onAction(BackupUiAction.StartRestore) },
            enabled = state.canRestore == true,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.backup_restore)) }
        if (state.canRestore == false) {
            Text(
                stringResource(R.string.backup_restore_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ReminderSection(state, onAction, hasPermission)
    }
    BackupDialogs(state, AuthPrompt(stringResource(R.string.backup_auth_title)), onAction)
}

@Composable
private fun StatusSection(state: BackupUiState, onAction: (BackupUiAction) -> Unit) {
    val status = state.status
    Text(lastBackupText(status), style = MaterialTheme.typography.titleMedium)
    if (status?.overdue == true) Text(stringResource(R.string.backup_overdue), color = MaterialTheme.colorScheme.error)
    when (val chain = state.chain) {
        ChainStatus.Unknown -> Text(stringResource(R.string.backup_chain_unknown))
        is ChainStatus.Intact -> Text(stringResource(R.string.backup_chain_intact, chain.eventCount))
        is ChainStatus.Broken -> {
            Text(stringResource(R.string.backup_chain_broken), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { onAction(BackupUiAction.RecheckChain) }) { Text(stringResource(R.string.backup_chain_check)) }
        }
    }
}

@Composable
private fun lastBackupText(status: BackupStatus?): String {
    if (status?.lastBackupAt == null) return stringResource(R.string.backup_last_none)
    val days = (status.daysSince ?: 0L).toInt()
    return if (days == 0) stringResource(R.string.backup_last_today) else pluralStringResource(R.plurals.backup_last_days, days, days)
}

@Composable
private fun ReminderSection(state: BackupUiState, onAction: (BackupUiAction) -> Unit, hasPermission: () -> Boolean) {
    Text(stringResource(R.string.backup_reminder_section), style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.backup_reminder_switch), modifier = Modifier.weight(1f))
        Switch(
            checked = state.reminderEnabled,
            onCheckedChange = { onAction(BackupUiAction.ToggleReminder(it, hasPermission())) },
        )
    }
    if (state.permissionDenied) Text(stringResource(R.string.backup_reminder_denied), color = MaterialTheme.colorScheme.error)
    if (state.reminderEnabled) {
        Text(stringResource(R.string.backup_reminder_interval))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            INTERVAL_OPTIONS.forEach { days ->
                FilterChip(
                    selected = state.intervalDays == days,
                    onClick = { onAction(BackupUiAction.SetIntervalDays(days)) },
                    label = { Text(pluralStringResource(R.plurals.backup_reminder_days, days, days)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

internal fun BackupMessage.textRes() = when (this) {
    BackupMessage.PASSWORD_TOO_SHORT -> R.string.backup_message_password_short
    BackupMessage.PASSWORD_MISMATCH -> R.string.backup_message_password_mismatch
    BackupMessage.AUTH_FAILED -> R.string.backup_message_auth_failed
    BackupMessage.WRONG_PASSWORD_OR_TAMPERED -> R.string.backup_message_wrong_password
    BackupMessage.TAMPERED_OR_TRUNCATED -> R.string.backup_message_tampered
    BackupMessage.NOT_A_BACKUP -> R.string.backup_message_not_a_backup
    BackupMessage.NEWER_VERSION -> R.string.backup_message_newer_version
    BackupMessage.TOO_LARGE -> R.string.backup_message_too_large
    BackupMessage.CORRUPT -> R.string.backup_message_corrupt
    BackupMessage.UNREADABLE -> R.string.backup_message_unreadable
    BackupMessage.RESTORE_NOT_ALLOWED -> R.string.backup_message_not_allowed
    BackupMessage.CREATE_FAILED -> R.string.backup_message_create_failed
    BackupMessage.VERIFY_FAILED -> R.string.backup_message_verify_failed
    BackupMessage.RESTORE_FAILED -> R.string.backup_message_restore_failed
}

@Preview(showBackground = true)
@Composable
private fun BackupContentPreview() {
    MyfinancesTheme { BackupContent(BackupUiState(canRestore = false, chain = ChainStatus.Intact(120)), onAction = {}, hasPermission = { true }) }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BackupContentDarkPreview() {
    MyfinancesTheme { BackupContent(BackupUiState(canRestore = true, reminderEnabled = true), onAction = {}, hasPermission = { true }) }
}
