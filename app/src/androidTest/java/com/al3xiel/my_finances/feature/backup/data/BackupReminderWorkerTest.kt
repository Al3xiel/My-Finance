package com.al3xiel.my_finances.feature.backup.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.al3xiel.my_finances.domain.model.event.AggregateType
import com.al3xiel.my_finances.domain.model.event.EventStore
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.model.event.StoredEvent
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RemindBackupOverdueUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant

private object EmptyStore : EventStore {
    override suspend fun append(correlationId: String, events: List<NewEvent>) = emptyList<StoredEvent>()
    override suspend fun readAll() = emptyList<StoredEvent>()
    override suspend fun readAfter(sequence: Long) = emptyList<StoredEvent>()
    override suspend fun history(aggregateType: AggregateType, aggregateId: String) = emptyList<StoredEvent>()
}

private class SwitchedOffBackupSettings : BackupSettingsRepository {
    override val enabled = MutableStateFlow(false)
    override val intervalDays = MutableStateFlow(BackupSettingsRepository.DEFAULT_INTERVAL_DAYS)
    override suspend fun isEnabled() = false
    override suspend fun setEnabled(enabled: Boolean) = Unit
    override suspend fun setIntervalDays(days: Int) = Unit
    override suspend fun lastRemindedAt(): Instant? = null
    override suspend fun markReminded(at: Instant) = Unit
}

@RunWith(AndroidJUnit4::class)
class BackupReminderWorkerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun factory(remind: RemindBackupOverdueUseCase) = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            BackupReminderWorker(appContext, workerParameters, remind)
    }

    @Test
    fun aRunWithTheReminderSwitchedOffSucceedsAndStaysQuiet() = runBlocking {
        val settings = SwitchedOffBackupSettings()
        val remind = RemindBackupOverdueUseCase(
            ObserveBackupStatusUseCase(EmptyStore, settings, Clock.systemUTC()),
            settings,
            { false },
            Clock.systemUTC(),
        )
        val worker = TestListenableWorkerBuilder<BackupReminderWorker>(context).setWorkerFactory(factory(remind)).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
