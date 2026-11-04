package com.al3xiel.my_finances.feature.backup.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxSize
import com.al3xiel.my_finances.ui.theme.LocalSpacing
import com.al3xiel.my_finances.core.designsystem.StatusBadge
import com.al3xiel.my_finances.core.designsystem.SectionRow
import com.al3xiel.my_finances.core.designsystem.SectionDivider
import com.al3xiel.my_finances.core.designsystem.SectionCard
import com.al3xiel.my_finances.core.designsystem.ScreenScaffold
import com.al3xiel.my_finances.core.designsystem.PillStyle
import com.al3xiel.my_finances.core.designsystem.PillButton
import com.al3xiel.my_finances.core.designsystem.BadgeStatus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

/** Días tras los que un respaldo se considera vencido; S70 y S05 ofrecen las mismas opciones. */
internal val INTERVAL_OPTIONS = listOf(7, 15, 30)

/** S70 sin estado: último respaldo, cadena, crear, restaurar y recordatorio. */
@Composable
fun BackupContent(
    state: BackupUiState,
    onAction: (BackupUiAction) -> Unit,
    hasPermission: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    ScreenScaffold(title = stringResource(R.string.backup_title), modifier = modifier) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            StatusSection(state, onAction)
            Text(stringResource(R.string.backup_explain), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = spacing.sm))
            state.message?.let { message ->
                Text(stringResource(message.textRes()), color = MaterialTheme.colorScheme.error)
            }
            PillButton(stringResource(R.string.backup_create), { onAction(BackupUiAction.StartCreate) }, Modifier.fillMaxWidth())
            PillButton(
                stringResource(R.string.backup_restore),
                { onAction(BackupUiAction.StartRestore) },
                Modifier.fillMaxWidth(),
                PillStyle.TONAL,
                enabled = state.canRestore == true,
                disabledReason = stringResource(R.string.backup_restore_unavailable).takeIf { state.canRestore == false },
            )
            if (state.canRestore == false) {
                Text(
                    stringResource(R.string.backup_restore_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.sm),
                )
            }
            ReminderSection(state, onAction, hasPermission)
        }
    }
    BackupDialogs(state, AuthPrompt(stringResource(R.string.backup_auth_title)), onAction)
}

@Composable
private fun StatusSection(state: BackupUiState, onAction: (BackupUiAction) -> Unit) {
    val status = state.status
    SectionCard {
        SectionRow(
            headline = lastBackupText(status),
            trailing = {
                when {
                    status?.lastBackupAt == null -> StatusBadge(BadgeStatus.PENDING, label = stringResource(R.string.backup_badge_none))
                    status.overdue -> StatusBadge(BadgeStatus.OVERDUE)
                    else -> StatusBadge(BadgeStatus.RECONCILED, label = stringResource(R.string.backup_badge_current))
                }
            },
        )
        if (status?.overdue == true) {
            SectionDivider()
            SectionRow(headline = stringResource(R.string.backup_overdue))
        }
        SectionDivider()
        when (val chain = state.chain) {
            ChainStatus.Unknown -> SectionRow(headline = stringResource(R.string.backup_chain_unknown))
            is ChainStatus.Intact -> SectionRow(
                headline = stringResource(R.string.backup_chain_intact, chain.eventCount),
                trailing = { StatusBadge(BadgeStatus.RECONCILED, label = stringResource(R.string.backup_chain_badge_ok)) },
            )
            is ChainStatus.Broken -> {
                SectionRow(
                    headline = stringResource(R.string.backup_chain_broken),
                    trailing = { StatusBadge(BadgeStatus.NEEDS_REVIEW) },
                )
                SectionDivider()
                SectionRow(headline = stringResource(R.string.backup_chain_check), onClick = { onAction(BackupUiAction.RecheckChain) })
            }
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
    val spacing = LocalSpacing.current
    SectionCard(title = stringResource(R.string.backup_reminder_section)) {
        SectionRow(
            headline = stringResource(R.string.backup_reminder_switch),
            modifier = Modifier.toggleable(
                value = state.reminderEnabled,
                onValueChange = { onAction(BackupUiAction.ToggleReminder(it, hasPermission())) },
                role = Role.Switch,
            ),
            trailing = { Switch(checked = state.reminderEnabled, onCheckedChange = null) },
        )
        if (state.permissionDenied) {
            SectionDivider()
            Text(
                stringResource(R.string.backup_reminder_denied),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(spacing.lg),
            )
        }
        if (state.reminderEnabled) {
            SectionDivider()
            Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(stringResource(R.string.backup_reminder_interval), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
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
@Preview(showBackground = true, fontScale = 2f)
@Composable
private fun BackupContentDarkPreview() {
    MyfinancesTheme { BackupContent(BackupUiState(canRestore = true, reminderEnabled = true), onAction = {}, hasPermission = { true }) }
}
