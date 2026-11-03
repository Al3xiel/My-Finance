package com.al3xiel.my_finances.feature.backup.ui

import com.al3xiel.my_finances.feature.backup.domain.format.BackupFormatException.Reason
import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.backup.domain.usecase.BackupSummary
import com.al3xiel.my_finances.feature.backup.domain.usecase.ChainStatus
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupResult
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestorePreview
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreResult

/** Contraseña mínima de un respaldo (D45). */
const val MIN_BACKUP_PASSWORD_LENGTH = 10

enum class BackupStage {
    IDLE,
    CREATE_PASSWORD,
    CREATE_PICKING,
    CREATE_PICKER_OPEN,
    CREATING,
    CREATED,
    RESTORE_PICKING,
    RESTORE_PICKER_OPEN,
    RESTORE_PASSWORD,
    RESTORE_CHECKING,
    RESTORE_CONFIRM,
    RESTORING,
}

enum class BackupMessage {
    PASSWORD_TOO_SHORT,
    PASSWORD_MISMATCH,
    AUTH_FAILED,
    WRONG_PASSWORD_OR_TAMPERED,
    TAMPERED_OR_TRUNCATED,
    NOT_A_BACKUP,
    NEWER_VERSION,
    TOO_LARGE,
    CORRUPT,
    UNREADABLE,
    RESTORE_NOT_ALLOWED,
    CREATE_FAILED,
    VERIFY_FAILED,
    RESTORE_FAILED,
}

data class BackupUiState(
    val status: BackupStatus? = null,
    val chain: ChainStatus = ChainStatus.Unknown,
    /** `null` mientras se consulta. */
    val canRestore: Boolean? = null,
    val reminderEnabled: Boolean = false,
    val intervalDays: Int = BackupSettingsRepository.DEFAULT_INTERVAL_DAYS,
    val stage: BackupStage = BackupStage.IDLE,
    val summary: BackupSummary? = null,
    val created: CreateBackupResult.Created? = null,
    val message: BackupMessage? = null,
    val showExplanation: Boolean = false,
    val requestPermission: Boolean = false,
    val permissionDenied: Boolean = false,
)

internal fun RestorePreview.message(): BackupMessage? = when (this) {
    is RestorePreview.Ready, RestorePreview.Cancelled -> null
    RestorePreview.NotAllowed -> BackupMessage.RESTORE_NOT_ALLOWED
    RestorePreview.Unreadable -> BackupMessage.UNREADABLE
    is RestorePreview.Rejected -> reason.message()
}

internal fun RestoreResult.message(): BackupMessage? = when (this) {
    is RestoreResult.Restored, RestoreResult.Cancelled -> null
    RestoreResult.NotAllowed -> BackupMessage.RESTORE_NOT_ALLOWED
    RestoreResult.Unreadable -> BackupMessage.UNREADABLE
    RestoreResult.Failed -> BackupMessage.RESTORE_FAILED
    is RestoreResult.Rejected -> reason.message()
}

internal fun Reason.message() = when (this) {
    Reason.WRONG_PASSWORD_OR_TAMPERED -> BackupMessage.WRONG_PASSWORD_OR_TAMPERED
    Reason.TAMPERED_OR_TRUNCATED -> BackupMessage.TAMPERED_OR_TRUNCATED
    Reason.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
    Reason.UNSUPPORTED_VERSION, Reason.NEWER_SCHEMA, Reason.UNKNOWN_EVENT_TYPE -> BackupMessage.NEWER_VERSION
    Reason.TOO_LARGE, Reason.TOO_MANY_EVENTS, Reason.LINE_TOO_LONG, Reason.DECOMPRESSED_TOO_LARGE -> BackupMessage.TOO_LARGE
    Reason.INVALID_JSON, Reason.COUNT_MISMATCH, Reason.CHAIN_BROKEN -> BackupMessage.CORRUPT
}
