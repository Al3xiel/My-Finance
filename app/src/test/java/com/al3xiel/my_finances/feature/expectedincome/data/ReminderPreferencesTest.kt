package com.al3xiel.my_finances.feature.expectedincome.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.preferences() = ReminderPreferences(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(StandardTestDispatcher(testScheduler))) {
            folder.newFile("reminders.preferences_pb").also { it.delete() }
        },
    )

    @Test
    fun theSwitchIsOffByDefaultAndPersists() = runTest {
        val preferences = preferences()
        assertFalse(preferences.isEnabled())

        preferences.setEnabled(true)
        assertTrue(preferences.isEnabled())
        preferences.setEnabled(false)
        assertFalse(preferences.isEnabled())
    }

    @Test
    fun countsAndRemembersTheLastNoticePerExpectedIncome() = runTest {
        val preferences = preferences()
        val e1 = ExpectedIncomeId("e1")
        val e2 = ExpectedIncomeId("e2")
        assertEquals(ReminderLog(0, null), preferences.log(e1))

        preferences.markReminded(listOf(e1, e2), LocalDate.of(2026, 11, 6))
        preferences.markReminded(listOf(e1), LocalDate.of(2026, 11, 9))

        assertEquals(ReminderLog(2, LocalDate.of(2026, 11, 9)), preferences.log(e1))
        assertEquals(ReminderLog(1, LocalDate.of(2026, 11, 6)), preferences.log(e2))
    }
}
