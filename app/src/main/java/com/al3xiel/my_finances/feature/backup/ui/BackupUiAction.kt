package com.al3xiel.my_finances.feature.backup.ui

import com.al3xiel.my_finances.core.security.AuthPrompt

/** Las contraseñas viajan como `CharArray` y el ViewModel las borra al terminar; nunca entran en el estado. */
sealed interface BackupUiAction {
    data object StartCreate : BackupUiAction
    class SubmitCreatePassword(val password: CharArray, val confirmation: CharArray, val prompt: AuthPrompt) : BackupUiAction
    data object CreatePickerOpened : BackupUiAction

    /** `uri` nulo = el usuario cerró el selector. */
    data class CreatePickerResult(val uri: String?) : BackupUiAction
    data object StartRestore : BackupUiAction
    data object RestorePickerOpened : BackupUiAction
    data class RestorePickerResult(val uri: String?) : BackupUiAction
    class SubmitRestorePassword(val password: CharArray) : BackupUiAction
    data object ConfirmRestore : BackupUiAction
    data object Cancel : BackupUiAction
    data object DismissMessage : BackupUiAction
    data object DismissCreated : BackupUiAction
    data object RecheckChain : BackupUiAction
    data class ToggleReminder(val on: Boolean, val permissionGranted: Boolean) : BackupUiAction
    data object AcceptExplanation : BackupUiAction
    data object DismissExplanation : BackupUiAction
    data object PermissionRequestLaunched : BackupUiAction
    data class PermissionResult(val granted: Boolean) : BackupUiAction
    data class SetIntervalDays(val days: Int) : BackupUiAction
}
