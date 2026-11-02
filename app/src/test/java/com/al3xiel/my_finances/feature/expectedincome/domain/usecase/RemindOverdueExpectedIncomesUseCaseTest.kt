package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncomeStatus
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderLog
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderNotifier
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class FakeReminderRepository(var on: Boolean = true) : ReminderRepository {
    val logs = mutableMapOf<ExpectedIncomeId, ReminderLog>()
    override val enabled: Flow<Boolean> get() = flowOf(on)
    override suspend fun isEnabled() = on
    override suspend fun setEnabled(enabled: Boolean) { on = enabled }
    override suspend fun log(id: ExpectedIncomeId) = logs[id] ?: ReminderLog(0, null)
    override suspend fun markReminded(ids: Collection<ExpectedIncomeId>, on: LocalDate) {
        ids.forEach { logs[it] = ReminderLog(log(it).count + 1, on) }
    }
}

private class FakeReminderNotifier(var canNotify: Boolean = true) : ReminderNotifier {
    var posted = 0
    override fun remind(): Boolean = canNotify.also { if (it) posted++ }
}

class RemindOverdueExpectedIncomesUseCaseTest {
    private val repo = FakeExpectedIncomeRepository()
    private val reminders = FakeReminderRepository()
    private val notifier = FakeReminderNotifier()
    private var today = LocalDate.of(2026, 11, 6)

    private fun run(): Int = kotlinx.coroutines.runBlocking {
        val clock = Clock.fixed(Instant.parse("${today}T15:00:00Z"), ZoneOffset.UTC)
        RemindOverdueExpectedIncomesUseCase(repo, reminders, notifier, clock)()
    }

    private fun overdue(id: String, to: LocalDate = LocalDate.of(2026, 11, 5)) =
        repo.put(id, AccountId("a"), IncomeSourceId("s"), to = to, status = ExpectedIncomeStatus.OVERDUE)

    @Test
    fun oneGroupedNoticeForAllTheOverdueOnesThatAreDue() = runTest {
        overdue("e1")
        overdue("e2")

        assertEquals(2, run())

        assertEquals(1, notifier.posted)
        assertEquals(1, reminders.log(ExpectedIncomeId("e1")).count)
        assertEquals(today, reminders.log(ExpectedIncomeId("e2")).lastRemindedOn)
    }

    @Test
    fun nothingWhenTheSwitchIsOff() = runTest {
        overdue("e1")
        reminders.on = false

        assertEquals(0, run())
        assertEquals(0, notifier.posted)
    }

    @Test
    fun nothingTheSameDayTheRangeEnds() = runTest {
        overdue("e1", to = today)

        assertEquals(0, run())
        assertEquals(0, notifier.posted)
    }

    @Test
    fun followsTheThreeDayRhythmAndStopsAtThree() = runTest {
        overdue("e1")
        val id = ExpectedIncomeId("e1")

        assertEquals(1, run())
        today = today.plusDays(2)
        assertEquals(0, run())
        today = today.plusDays(1)
        assertEquals(1, run())
        today = today.plusDays(3)
        assertEquals(1, run())
        today = today.plusDays(3)
        assertEquals(0, run())

        assertEquals(3, reminders.log(id).count)
        assertEquals(3, notifier.posted)
    }

    @Test
    fun aDeniedPermissionDoesNotSpendTheNotices() = runTest {
        overdue("e1")
        notifier.canNotify = false

        assertEquals(0, run())
        assertEquals(0, reminders.log(ExpectedIncomeId("e1")).count)

        notifier.canNotify = true
        assertEquals(1, run())
    }

    @Test
    fun onlyTheOnesWhoseTurnItIsAreMarked() = runTest {
        overdue("e1")
        reminders.logs[ExpectedIncomeId("e1")] = ReminderLog(1, today.minusDays(1))
        overdue("e2")

        assertEquals(1, run())

        assertEquals(1, reminders.log(ExpectedIncomeId("e1")).count)
        assertEquals(1, reminders.log(ExpectedIncomeId("e2")).count)
    }
}
