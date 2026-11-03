package com.al3xiel.my_finances.feature.backup.domain

import app.cash.turbine.test
import com.al3xiel.my_finances.domain.model.event.BackupCreated
import com.al3xiel.my_finances.domain.model.event.BackupTimes
import com.al3xiel.my_finances.domain.model.event.NewEvent
import com.al3xiel.my_finances.domain.usecase.FakeEventStore
import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import com.al3xiel.my_finances.feature.backup.domain.usecase.ObserveBackupStatusUseCase
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

class BackupStatusTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z")

    private fun daysAgo(days: Long) = now.minus(Duration.ofDays(days)).toEpochMilli()

    @Test
    fun withoutEventsThereIsNothingToBackUp() {
        val status = BackupStatus.from(BackupTimes(null, null), now, 15)

        assertNull(status.lastBackupAt)
        assertNull(status.daysSince)
        assertFalse(status.overdue)
    }

    @Test
    fun withoutABackupItCountsFromTheFirstEvent() {
        val status = BackupStatus.from(BackupTimes(firstEventAt = daysAgo(20), lastBackupAt = null), now, 15)

        assertNull(status.lastBackupAt)
        assertEquals(20L, status.daysSince)
        assertTrue(status.overdue)
    }

    @Test
    fun theLastBackupWinsOverTheFirstEvent() {
        val status = BackupStatus.from(BackupTimes(daysAgo(90), daysAgo(3)), now, 15)

        assertEquals(Instant.ofEpochMilli(daysAgo(3)), status.lastBackupAt)
        assertEquals(3L, status.daysSince)
        assertFalse(status.overdue)
    }

    @Test
    fun overdueStartsExactlyAtTheInterval() {
        assertFalse(BackupStatus.from(BackupTimes(daysAgo(14), null), now, 15).overdue)
        assertTrue(BackupStatus.from(BackupTimes(daysAgo(15), null), now, 15).overdue)
    }

    @Test
    fun theUseCaseUsesTheEventsAndTheConfiguredInterval() = runTest {
        val store = FakeEventStore()
        store.append("c", listOf(NewEvent(BackupCreated("b", 1, "h"), LocalDate.of(2026, 11, 1))))
        val clock = Clock.fixed(Instant.ofEpochMilli(0).plus(Duration.ofDays(10)).plus(Duration.ofHours(1)), ZoneOffset.UTC)
        val settings = FakeBackupSettings(intervalDays = 7)

        ObserveBackupStatusUseCase(store, settings, clock)().test {
            val status = awaitItem()
            assertTrue(status.overdue)
            assertEquals(10L, status.daysSince)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
