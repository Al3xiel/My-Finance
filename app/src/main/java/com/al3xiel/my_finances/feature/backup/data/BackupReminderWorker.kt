package com.al3xiel.my_finances.feature.backup.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.al3xiel.my_finances.feature.backup.domain.usecase.RemindBackupOverdueUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Corrida diaria del recordatorio de respaldo (D47). Abre la base con la app bloqueada y no registra nada en logs.
 * Un fallo técnico se reintenta con el retroceso de WorkManager.
 */
@HiltWorker
class BackupReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remind: RemindBackupOverdueUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        remind()
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "backup_reminder"
    }
}
