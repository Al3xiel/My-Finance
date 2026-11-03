package com.al3xiel.my_finances.feature.backup.domain.model

import com.al3xiel.my_finances.domain.model.event.BackupTimes
import java.time.Duration
import java.time.Instant

/**
 * Cuándo fue el último respaldo, derivado de los eventos (P9). Sin respaldo cuenta desde el primer evento; sin eventos
 * no hay nada que respaldar y nunca está vencido.
 */
data class BackupStatus(
    val lastBackupAt: Instant?,
    val referenceAt: Instant?,
    val daysSince: Long?,
    val overdue: Boolean,
) {
    companion object {
        fun from(times: BackupTimes, now: Instant, intervalDays: Int): BackupStatus {
            val last = times.lastBackupAt?.let(Instant::ofEpochMilli)
            val reference = last ?: times.firstEventAt?.let(Instant::ofEpochMilli)
            val days = reference?.let { Duration.between(it, now).toDays().coerceAtLeast(0) }
            return BackupStatus(last, reference, days, overdue = days != null && days >= intervalDays)
        }
    }
}
