package com.al3xiel.my_finances.feature.expectedincome.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderLog
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.usecase.RemindOverdueExpectedIncomesUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

private object EmptyExpectedIncomes : ExpectedIncomeRepository {
    override suspend fun find(id: ExpectedIncomeId): ExpectedIncome? = null
    override fun observeAll(): Flow<List<ExpectedIncome>> = flowOf(emptyList())
    override suspend fun pendingFor(accountId: AccountId, incomeSourceId: IncomeSourceId) = emptyList<ExpectedIncome>()
    override suspend fun overdue() = emptyList<ExpectedIncome>()
    override suspend fun next(): ExpectedIncome? = null
    override suspend fun confirmedBy(movementId: MovementId): ExpectedIncome? = null
}

private object SwitchedOff : ReminderRepository {
    override val enabled: Flow<Boolean> = flowOf(false)
    override suspend fun isEnabled() = false
    override suspend fun setEnabled(enabled: Boolean) = Unit
    override suspend fun log(id: ExpectedIncomeId) = ReminderLog(0, null)
    override suspend fun markReminded(ids: Collection<ExpectedIncomeId>, on: LocalDate) = Unit
}

@RunWith(AndroidJUnit4::class)
class ExpectedIncomeReminderWorkerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun factory(remind: RemindOverdueExpectedIncomesUseCase) = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
            ExpectedIncomeReminderWorker(appContext, workerParameters, remind)
    }

    @Test
    fun aRunWithNothingToWarnAboutSucceeds() = runBlocking {
        val remind = RemindOverdueExpectedIncomesUseCase(
            expectedIncomes = EmptyExpectedIncomes,
            reminders = SwitchedOff,
            notifier = { false },
            clock = java.time.Clock.systemUTC(),
        )
        val worker = TestListenableWorkerBuilder<ExpectedIncomeReminderWorker>(context).setWorkerFactory(factory(remind)).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
    }
}
