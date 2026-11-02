package com.al3xiel.my_finances.feature.backup.domain

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
import com.al3xiel.my_finances.feature.backup.domain.usecase.RemindBackupOverdueUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RemindBackupOverdueUseCaseTest {
    private val store = FakeEventStore()
    private val settings = FakeBackupSettings(enabled = true, intervalDays = 15)
    private var published = 0
    private var permission = true
    private val notifier = com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderNotifier {
        if (permission) published++
        permission
    }
    private val clock = Clock.fixed(Instant.ofEpochMilli(0).plus(Duration.ofDays(20)), ZoneOffset.UTC)
    private val remind = RemindBackupOverdueUseCase(ObserveBackupStatusUseCase(store, settings, clock), settings, notifier, clock)

    private suspend fun oneEvent() {
        store.append("c", listOf(NewEvent(AccountRenamed(AccountId("a"), "x"), LocalDate.of(2026, 11, 1))))
    }

    @Test
    fun remindsOnceWhenOverdueAndRecordsIt() = runTest {
        oneEvent()

        assertTrue(remind())
        assertFalse(remind())

        assertEquals(1, published)
        assertEquals(clock.instant(), settings.lastReminded)
    }

    @Test
    fun staysQuietWhenSwitchedOffOrThereIsNothingToBackUp() = runTest {
        assertFalse(remind())
        oneEvent()
        settings.setEnabled(false)
        assertFalse(remind())

        assertEquals(0, published)
    }

    @Test
    fun aDeniedPermissionDoesNotSpendTheReminder() = runTest {
        oneEvent()
        permission = false

        assertFalse(remind())

        assertNull(settings.lastReminded)
    }
}
