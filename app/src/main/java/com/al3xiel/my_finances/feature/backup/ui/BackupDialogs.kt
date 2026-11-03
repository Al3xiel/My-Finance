package com.al3xiel.my_finances.feature.backup.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.security.AuthPrompt

/** Los diálogos de cada paso; los pasos que abren el selector del sistema los lanza [BackupScreen]. */
@Composable
internal fun BackupDialogs(state: BackupUiState, prompt: AuthPrompt, onAction: (BackupUiAction) -> Unit) {
    when (state.stage) {
        BackupStage.CREATE_PASSWORD -> CreatePasswordDialog(state.message, prompt, onAction)
        BackupStage.RESTORE_PASSWORD -> RestorePasswordDialog(state.message, onAction)
        BackupStage.CREATING, BackupStage.RESTORE_CHECKING -> BusyDialog(R.string.backup_working)
        BackupStage.RESTORING -> BusyDialog(R.string.backup_restoring)
        BackupStage.CREATED -> CreatedDialog(state, onAction)
        BackupStage.RESTORE_CONFIRM -> ConfirmRestoreDialog(state, onAction)
        BackupStage.IDLE, BackupStage.CREATE_PICKING, BackupStage.CREATE_PICKER_OPEN,
        BackupStage.RESTORE_PICKING, BackupStage.RESTORE_PICKER_OPEN -> Unit
    }
    if (state.showExplanation) {
        AlertDialog(
            onDismissRequest = { onAction(BackupUiAction.DismissExplanation) },
            title = { Text(stringResource(R.string.backup_reminder_explanation_title)) },
            text = { Text(stringResource(R.string.backup_reminder_explanation)) },
            confirmButton = {
                TextButton(onClick = { onAction(BackupUiAction.AcceptExplanation) }) {
                    Text(stringResource(R.string.backup_reminder_explanation_ok))
                }
            },
            dismissButton = { CancelButton { onAction(BackupUiAction.DismissExplanation) } },
        )
    }
}

@Composable
private fun CreatePasswordDialog(message: BackupMessage?, prompt: AuthPrompt, onAction: (BackupUiAction) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onAction(BackupUiAction.Cancel) },
        title = { Text(stringResource(R.string.backup_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_password_hint), style = MaterialTheme.typography.bodySmall)
                PasswordField(password, R.string.backup_password_label) { password = it }
                PasswordField(confirmation, R.string.backup_password_confirm_label) { confirmation = it }
                message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                onAction(BackupUiAction.SubmitCreatePassword(password.toCharArray(), confirmation.toCharArray(), prompt))
                password = ""
                confirmation = ""
            }) { Text(stringResource(R.string.backup_password_ok)) }
        },
        dismissButton = { CancelButton { onAction(BackupUiAction.Cancel) } },
    )
}

@Composable
private fun RestorePasswordDialog(message: BackupMessage?, onAction: (BackupUiAction) -> Unit) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onAction(BackupUiAction.Cancel) },
        title = { Text(stringResource(R.string.backup_restore_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PasswordField(password, R.string.backup_password_label) { password = it }
                message?.let { Text(stringResource(it.textRes()), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                onAction(BackupUiAction.SubmitRestorePassword(password.toCharArray()))
                password = ""
            }) { Text(stringResource(R.string.backup_password_ok)) }
        },
        dismissButton = { CancelButton { onAction(BackupUiAction.Cancel) } },
    )
}

@Composable
private fun PasswordField(value: String, label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun BusyDialog(text: Int) {
    AlertDialog(onDismissRequest = {}, text = { Text(stringResource(text)) }, confirmButton = {})
}

@Composable
private fun CreatedDialog(state: BackupUiState, onAction: (BackupUiAction) -> Unit) {
    val created = state.created
    AlertDialog(
        onDismissRequest = { onAction(BackupUiAction.DismissCreated) },
        title = { Text(stringResource(R.string.backup_created_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_created_body, created?.eventCount ?: 0L))
                if (created?.chainIntact == false) Text(stringResource(R.string.backup_created_chain_broken))
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(BackupUiAction.DismissCreated) }) { Text(stringResource(R.string.backup_ok)) }
        },
    )
}

@Composable
private fun ConfirmRestoreDialog(state: BackupUiState, onAction: (BackupUiAction) -> Unit) {
    val summary = state.summary
    AlertDialog(
        onDismissRequest = { onAction(BackupUiAction.Cancel) },
        title = { Text(stringResource(R.string.backup_restore_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary != null) {
                    Text(
                        stringResource(
                            R.string.backup_restore_confirm_body,
                            summary.eventCount,
                            summary.firstOccurredOn.orEmpty(),
                            summary.lastOccurredOn.orEmpty(),
                        ),
                    )
                    if (!summary.chainIntact) Text(stringResource(R.string.backup_restore_confirm_chain_broken))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAction(BackupUiAction.ConfirmRestore) }) { Text(stringResource(R.string.backup_restore_confirm)) }
        },
        dismissButton = { CancelButton { onAction(BackupUiAction.Cancel) } },
    )
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(stringResource(R.string.backup_password_cancel)) }
}
