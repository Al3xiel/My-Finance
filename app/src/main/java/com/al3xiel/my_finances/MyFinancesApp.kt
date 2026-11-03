package com.al3xiel.my_finances

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.al3xiel.my_finances.core.share.SharedFilesCleaner
import com.al3xiel.my_finances.core.startup.SeedInitializer
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyFinancesApp : Application(), Configuration.Provider {
    @Inject lateinit var seedInitializer: SeedInitializer
    @Inject lateinit var sharedFilesCleaner: SharedFilesCleaner
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var backupSettings: BackupSettingsRepository
    @Inject lateinit var backupReminderScheduler: BackupReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob())

    /** WorkManager se inicia a pedido con la fábrica de Hilt; el inicializador automático está quitado del manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        seedInitializer.start(appScope)
        // Rendiciones que quedaron en la caché de una sesión anterior (docs/02 S7).
        appScope.launch { sharedFilesCleaner.clear() }
        // Tras reiniciar, actualizar la app o restaurar un respaldo la corrida diaria puede haberse perdido.
        appScope.launch {
            if (reminders.isEnabled()) reminderScheduler.ensureScheduled() else reminderScheduler.cancel()
        }
        appScope.launch {
            if (backupSettings.isEnabled()) backupReminderScheduler.ensureScheduled() else backupReminderScheduler.cancel()
        }
    }
}
