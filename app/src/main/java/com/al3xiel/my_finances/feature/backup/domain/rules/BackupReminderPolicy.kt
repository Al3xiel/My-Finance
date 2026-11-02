package com.al3xiel.my_finances.feature.backup.domain.rules

import com.al3xiel.my_finances.feature.backup.domain.model.BackupStatus
import java.time.Duration
import java.time.Instant

/** Avisa al vencer y repite cada 7 días (D47). Un aviso anterior al último respaldo no cuenta para el ciclo nuevo. */
object BackupReminderPolicy {
    const val REPEAT_DAYS = 7L

    fun isDue(status: BackupStatus, lastRemindedAt: Instant?, now: Instant): Boolean {
        if (!status.overdue) return false
        val last = lastRemindedAt ?: return true
        val backup = status.lastBackupAt
        if (backup != null && last < backup) return true
        return Duration.between(last, now).toDays() >= REPEAT_DAYS
    }
}
