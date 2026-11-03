package com.al3xiel.my_finances.feature.backup.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** Ajustes del recordatorio (D47). Nada sensible: un interruptor, un número de días y la hora del último aviso. */
interface BackupSettingsRepository {
    val enabled: Flow<Boolean>
    val intervalDays: Flow<Int>

    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setIntervalDays(days: Int)

    suspend fun lastRemindedAt(): Instant?

    suspend fun markReminded(at: Instant)

    companion object {
        const val DEFAULT_INTERVAL_DAYS = 15
    }
}
