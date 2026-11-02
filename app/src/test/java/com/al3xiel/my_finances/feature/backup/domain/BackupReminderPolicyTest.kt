package com.al3xiel.my_finances.feature.backup.domain

import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import com.al3xiel.my_finances.feature.backup.domain.rules.BackupReminderPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class BackupReminderPolicyTest {
    private val now = Instant.parse("2026-11-02T12:00:00Z")
    private fun ago(days: Long) = now.minus(Duration.ofDays(days))
    private fun status(overdue: Boolean, lastBackupAt: Instant? = null) = BackupStatus(lastBackupAt, ago(20), 20, overdue)

    @Test
    fun nothingIsDueWhileTheBackupIsRecent() {
        assertFalse(BackupReminderPolicy.isDue(status(overdue = false), lastRemindedAt = null, now = now))
    }

    @Test
    fun theFirstOverdueRunReminds() {
        assertTrue(BackupReminderPolicy.isDue(status(overdue = true), null, now))
    }

    @Test
    fun itRepeatsOnlyAfterSevenDays() {
        assertFalse(BackupReminderPolicy.isDue(status(true), ago(6), now))
        assertTrue(BackupReminderPolicy.isDue(status(true), ago(7), now))
    }

    @Test
    fun aReminderFromBeforeTheLastBackupDoesNotCountForTheNewCycle() {
        assertTrue(BackupReminderPolicy.isDue(status(true, lastBackupAt = ago(2)), lastRemindedAt = ago(3), now = now))
    }
}
