package com.al3xiel.my_finances.feature.expectedincome.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Lo que corre en lugar del Worker real: aquí solo importa cuántos trabajos quedan programados, no lo que hacen. */
private class NoopWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = Result.success()
}

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scheduler = WorkManagerReminderScheduler(context)

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                NoopWorker(appContext, workerParameters)
        }
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setWorkerFactory(factory).build())
    }

    private fun scheduled() = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(ExpectedIncomeReminderWorker.UNIQUE_NAME).get()
        .filter { it.state != WorkInfo.State.CANCELLED }

    @Test
    fun schedulingTwiceKeepsASingleDailyJob() {
        scheduler.ensureScheduled()
        scheduler.ensureScheduled()

        assertEquals(1, scheduled().size)
    }

    @Test
    fun cancelRemovesIt() {
        scheduler.ensureScheduled()

        scheduler.cancel()

        // La cancelación corre en el ejecutor de WorkManager: se espera a que se refleje.
        val deadline = System.currentTimeMillis() + 5_000
        while (scheduled().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(scheduled().map { it.state }.toString(), 0, scheduled().size)
    }
}
