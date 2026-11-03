package com.al3xiel.my_finances.feature.expectedincome.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.RemindOverdueExpectedIncomesUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Corrida diaria del recordatorio. Abre la base con la app bloqueada (la clave no pide autenticación) y no registra
 * nada en logs: ni montos, ni nombres, ni payloads. Un fallo técnico se reintenta con el retroceso de WorkManager.
 */
@HiltWorker
class ExpectedIncomeReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remind: RemindOverdueExpectedIncomesUseCase,
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
        const val UNIQUE_NAME = "expected_income_reminder"
    }
}
