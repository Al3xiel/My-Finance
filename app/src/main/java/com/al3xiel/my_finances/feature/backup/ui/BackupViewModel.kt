package com.al3xiel.my_finances.feature.backup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.al3xiel.my_finances.core.di.IoDispatcher
import com.al3xiel.my_finances.core.security.AuthAvailability
import com.al3xiel.my_finances.core.security.AuthPrompt
import com.al3xiel.my_finances.core.security.AuthResult
import com.al3xiel.my_finances.core.security.Authenticator
import com.al3xiel.my_finances.core.security.ExternalFlowGuard
import com.al3xiel.my_finances.feature.backup.data.ChainIntegrityMonitor
import com.al3xiel.my_finances.feature.backup.data.ChainStatus
import com.al3xiel.my_finances.feature.backup.data.format.BackupFormatException.Reason
import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.backup.domain.usecase.BackupSummary
import com.al3xiel.my_finances.feature.backup.domain.usecase.CanRestoreUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupResult
import com.al3xiel.my_finances.feature.backup.domain.usecase.CreateBackupUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreBackupUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestorePreview
import com.al3xiel.my_finances.feature.backup.domain.usecase.RestoreResult
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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

/** S70: respaldar, restaurar y recordatorio (D44–D49). La re-autenticación del bloqueo (D48) se pide aquí antes de respaldar. */
@HiltViewModel
class BackupViewModel @Inject constructor(
    observeStatus: ObserveBackupStatusUseCase,
    private val monitor: ChainIntegrityMonitor,
    private val canRestore: CanRestoreUseCase,
    private val createBackup: CreateBackupUseCase,
    private val restoreBackup: RestoreBackupUseCase,
    private val settings: BackupSettingsRepository,
    private val scheduler: BackupReminderScheduler,
    private val securitySettings: SecuritySettingsRepository,
    private val authenticator: Authenticator,
    private val externalFlow: ExternalFlowGuard,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private data class Form(
        val canRestore: Boolean? = null,
        val stage: BackupStage = BackupStage.IDLE,
        val summary: BackupSummary? = null,
        val created: CreateBackupResult.Created? = null,
        val message: BackupMessage? = null,
        val showExplanation: Boolean = false,
        val requestPermission: Boolean = false,
        val permissionDenied: Boolean = false,
    )

    private val form = MutableStateFlow(Form())
    private var password: CharArray? = null
    private var restoreUri: String? = null

    val uiState: StateFlow<BackupUiState> = combine(
        observeStatus(),
        monitor.status,
        settings.enabled,
        settings.intervalDays,
        form,
    ) { status, chain, enabled, days, form ->
        BackupUiState(
            status, chain, form.canRestore, enabled, days, form.stage, form.summary, form.created,
            form.message, form.showExplanation, form.requestPermission, form.permissionDenied,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    init {
        viewModelScope.launch { monitor.checkOnce() }
        refreshCanRestore()
    }

    fun onAction(action: BackupUiAction) {
        when (action) {
            BackupUiAction.StartCreate -> form.update { it.copy(stage = BackupStage.CREATE_PASSWORD, message = null) }
            is BackupUiAction.SubmitCreatePassword -> submitCreate(action)
            BackupUiAction.CreatePickerOpened -> pickerOpened(BackupStage.CREATE_PICKING, BackupStage.CREATE_PICKER_OPEN)
            is BackupUiAction.CreatePickerResult -> createPicked(action.uri)
            BackupUiAction.StartRestore -> startRestore()
            BackupUiAction.RestorePickerOpened -> pickerOpened(BackupStage.RESTORE_PICKING, BackupStage.RESTORE_PICKER_OPEN)
            is BackupUiAction.RestorePickerResult -> restorePicked(action.uri)
            is BackupUiAction.SubmitRestorePassword -> checkRestore(action.password)
            BackupUiAction.ConfirmRestore -> confirmRestore()
            BackupUiAction.Cancel -> cancel()
            BackupUiAction.DismissMessage -> form.update { it.copy(message = null) }
            BackupUiAction.DismissCreated -> form.update { it.copy(stage = BackupStage.IDLE, created = null) }
            BackupUiAction.RecheckChain -> viewModelScope.launch { monitor.recheck() }
            else -> onReminderAction(action)
        }
    }

    private fun onReminderAction(action: BackupUiAction) {
        when (action) {
            is BackupUiAction.ToggleReminder -> when {
                !action.on -> setReminder(false)
                action.permissionGranted -> setReminder(true)
                else -> form.update { it.copy(showExplanation = true, permissionDenied = false) }
            }
            BackupUiAction.AcceptExplanation -> form.update { it.copy(showExplanation = false, requestPermission = true) }
            BackupUiAction.DismissExplanation -> form.update { it.copy(showExplanation = false) }
            BackupUiAction.PermissionRequestLaunched -> form.update { it.copy(requestPermission = false) }
            is BackupUiAction.PermissionResult ->
                if (action.granted) setReminder(true) else form.update { it.copy(permissionDenied = true) }
            is BackupUiAction.SetIntervalDays -> viewModelScope.launch { settings.setIntervalDays(action.days) }
            else -> Unit
        }
    }

    private fun setReminder(on: Boolean) {
        form.update { it.copy(permissionDenied = false) }
        viewModelScope.launch {
            settings.setEnabled(on)
            if (on) scheduler.ensureScheduled() else scheduler.cancel()
        }
    }

    private fun refreshCanRestore() {
        viewModelScope.launch {
            val allowed = withContext(ioDispatcher) { canRestore() }
            form.update { it.copy(canRestore = allowed) }
        }
    }

    private fun submitCreate(action: BackupUiAction.SubmitCreatePassword) {
        if (form.value.stage != BackupStage.CREATE_PASSWORD) return wipe(action.password, action.confirmation)
        val problem = when {
            action.password.size < MIN_BACKUP_PASSWORD_LENGTH -> BackupMessage.PASSWORD_TOO_SHORT
            !action.password.contentEquals(action.confirmation) -> BackupMessage.PASSWORD_MISMATCH
            else -> null
        }
        wipe(action.confirmation)
        if (problem != null) {
            wipe(action.password)
            form.update { it.copy(message = problem) }
            return
        }
        viewModelScope.launch {
            if (!reauthenticate(action.prompt)) {
                wipe(action.password)
                return@launch
            }
            password = action.password
            form.update { it.copy(stage = BackupStage.CREATE_PICKING, message = null) }
        }
    }

    /** Con el bloqueo activo, sacar todo el historial pide la credencial otra vez (D48). */
    private suspend fun reauthenticate(prompt: AuthPrompt): Boolean {
        val lockOn = securitySettings.settings.first().lockEnabled
        if (!lockOn || authenticator.availability() != AuthAvailability.READY) return true
        return when (authenticator.authenticate(prompt)) {
            AuthResult.Success -> true
            AuthResult.Cancelled -> {
                form.update { it.copy(stage = BackupStage.IDLE) }
                false
            }
            AuthResult.LockedOut, AuthResult.Failed -> {
                form.update { it.copy(stage = BackupStage.IDLE, message = BackupMessage.AUTH_FAILED) }
                false
            }
        }
    }

    private fun pickerOpened(from: BackupStage, to: BackupStage) {
        if (form.value.stage != from) return
        externalFlow.begin()
        form.update { it.copy(stage = to) }
    }

    private fun createPicked(uri: String?) {
        externalFlow.end()
        val secret = password
        if (uri == null || secret == null || form.value.stage !in PICKING_CREATE) {
            reset()
            return
        }
        form.update { it.copy(stage = BackupStage.CREATING) }
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) { createBackup(uri, secret) }
            } finally {
                reset(keepStage = true)
            }
            form.update {
                when (result) {
                    is CreateBackupResult.Created -> it.copy(stage = BackupStage.CREATED, created = result)
                    CreateBackupResult.Cancelled -> it.copy(stage = BackupStage.IDLE)
                    is CreateBackupResult.WriteFailed -> it.copy(stage = BackupStage.IDLE, message = BackupMessage.CREATE_FAILED)
                    is CreateBackupResult.VerificationFailed -> it.copy(stage = BackupStage.IDLE, message = BackupMessage.VERIFY_FAILED)
                }
            }
        }
    }

    private fun startRestore() {
        if (form.value.canRestore != true) {
            form.update { it.copy(message = BackupMessage.RESTORE_NOT_ALLOWED) }
            return
        }
        form.update { it.copy(stage = BackupStage.RESTORE_PICKING, message = null) }
    }

    private fun restorePicked(uri: String?) {
        externalFlow.end()
        if (uri == null || form.value.stage !in PICKING_RESTORE) {
            reset()
            return
        }
        restoreUri = uri
        form.update { it.copy(stage = BackupStage.RESTORE_PASSWORD) }
    }

    private fun checkRestore(secret: CharArray) {
        val uri = restoreUri
        if (uri == null || form.value.stage != BackupStage.RESTORE_PASSWORD) return wipe(secret)
        password = secret
        form.update { it.copy(stage = BackupStage.RESTORE_CHECKING, message = null) }
        viewModelScope.launch {
            val preview = withContext(ioDispatcher) { restoreBackup.preview(uri, secret) }
            if (preview is RestorePreview.Ready) {
                form.update { it.copy(stage = BackupStage.RESTORE_CONFIRM, summary = preview.summary) }
                return@launch
            }
            wipe(secret)
            password = null
            val retry = preview is RestorePreview.Rejected && preview.reason == Reason.WRONG_PASSWORD_OR_TAMPERED
            form.update { it.copy(stage = if (retry) BackupStage.RESTORE_PASSWORD else BackupStage.IDLE, message = preview.message()) }
            if (!retry) restoreUri = null
        }
    }

    private fun confirmRestore() {
        val uri = restoreUri
        val secret = password
        if (uri == null || secret == null || form.value.stage != BackupStage.RESTORE_CONFIRM) return
        form.update { it.copy(stage = BackupStage.RESTORING) }
        viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) { restoreBackup(uri, secret) }
            } finally {
                reset(keepStage = true)
            }
            // Si restauró, la app se reinicia sola: la pantalla se queda en "Restaurando".
            if (result !is RestoreResult.Restored) {
                form.update { it.copy(stage = BackupStage.IDLE, summary = null, message = result.message()) }
            }
        }
    }

    private fun cancel() {
        externalFlow.end()
        reset()
    }

    private fun reset(keepStage: Boolean = false) {
        password?.let { wipe(it) }
        password = null
        restoreUri = null
        if (!keepStage) form.update { it.copy(stage = BackupStage.IDLE, summary = null, created = null) }
    }

    private companion object {
        val PICKING_CREATE = setOf(BackupStage.CREATE_PICKING, BackupStage.CREATE_PICKER_OPEN)
        val PICKING_RESTORE = setOf(BackupStage.RESTORE_PICKING, BackupStage.RESTORE_PICKER_OPEN)
    }

    private fun wipe(vararg secrets: CharArray) = secrets.forEach { it.fill('\u0000') }

    private fun RestorePreview.message(): BackupMessage? = when (this) {
        is RestorePreview.Ready, RestorePreview.Cancelled -> null
        RestorePreview.NotAllowed -> BackupMessage.RESTORE_NOT_ALLOWED
        RestorePreview.Unreadable -> BackupMessage.UNREADABLE
        is RestorePreview.Rejected -> reason.message()
    }

    private fun RestoreResult.message(): BackupMessage? = when (this) {
        is RestoreResult.Restored, RestoreResult.Cancelled -> null
        RestoreResult.NotAllowed -> BackupMessage.RESTORE_NOT_ALLOWED
        RestoreResult.Unreadable -> BackupMessage.UNREADABLE
        RestoreResult.Failed -> BackupMessage.RESTORE_FAILED
        is RestoreResult.Rejected -> reason.message()
    }

    private fun Reason.message() = when (this) {
        Reason.WRONG_PASSWORD_OR_TAMPERED -> BackupMessage.WRONG_PASSWORD_OR_TAMPERED
        Reason.TAMPERED_OR_TRUNCATED -> BackupMessage.TAMPERED_OR_TRUNCATED
        Reason.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
        Reason.UNSUPPORTED_VERSION, Reason.NEWER_SCHEMA, Reason.UNKNOWN_EVENT_TYPE -> BackupMessage.NEWER_VERSION
        Reason.TOO_LARGE, Reason.TOO_MANY_EVENTS, Reason.LINE_TOO_LONG, Reason.DECOMPRESSED_TOO_LARGE -> BackupMessage.TOO_LARGE
        Reason.INVALID_JSON, Reason.COUNT_MISMATCH, Reason.CHAIN_BROKEN -> BackupMessage.CORRUPT
    }
}
