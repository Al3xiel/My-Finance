package com.al3xiel.my_finances.feature.backup.data

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Una sola corrida diaria con nombre único `backup_reminder`: `KEEP` evita duplicar al re-encolar al arrancar. */
class WorkManagerBackupReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupReminderScheduler {
    override fun ensureScheduled() {
        val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(BackupReminderWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(BackupReminderWorker.UNIQUE_NAME)
    }
}
