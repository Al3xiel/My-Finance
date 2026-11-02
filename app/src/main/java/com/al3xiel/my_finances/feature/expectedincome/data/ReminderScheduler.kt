package com.al3xiel.my_finances.feature.expectedincome.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Una sola corrida diaria con nombre único: `KEEP` hace que re-encolar al arrancar no duplique ni reinicie el plazo. */
class WorkManagerReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ReminderScheduler {
    override fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<ExpectedIncomeReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(ExpectedIncomeReminderWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ExpectedIncomeReminderWorker.UNIQUE_NAME)
    }
}
