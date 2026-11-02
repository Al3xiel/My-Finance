package com.al3xiel.my_finances.feature.expectedincome.data

import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderScheduler
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderWipeStepTest {
    private class FakeScheduler : ReminderScheduler {
        var cancelled = 0
        override fun ensureScheduled() = Unit
        override fun cancel() { cancelled++ }
    }

    @Test
    fun cancelsTheWorker() = runTest {
        val scheduler = FakeScheduler()

        ReminderWipeStep(scheduler).wipe()

        assertEquals(1, scheduler.cancelled)
    }

    @Test
    fun runsBeforeTheDatabaseKeyIsDeleted() {
        val step = ReminderWipeStep(FakeScheduler())

        assertTrue(step.priority < WipeStep.KEY_PRIORITY)
        assertEquals("expected_income_reminders", step.id)
    }
}
