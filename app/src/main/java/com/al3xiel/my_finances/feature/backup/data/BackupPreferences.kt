package com.al3xiel.my_finances.feature.backup.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.al3xiel.my_finances.domain.usecase.RestoreMarker
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ajustes del recordatorio y el marcador `RestorePending` en el DataStore de ajustes (no sensible). El borrado total
 * limpia ese DataStore entero (`PreferencesWipeStep`).
 */
@Singleton
class BackupPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BackupSettingsRepository, RestoreMarker {
    override val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }

    override val intervalDays: Flow<Int> =
        dataStore.data.map { it[INTERVAL_DAYS] ?: BackupSettingsRepository.DEFAULT_INTERVAL_DAYS }

    override suspend fun isEnabled() = enabled.first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    override suspend fun setIntervalDays(days: Int) {
        dataStore.edit { it[INTERVAL_DAYS] = days.coerceIn(MIN_DAYS, MAX_DAYS) }
    }

    override suspend fun lastRemindedAt(): Instant? = dataStore.data.first()[LAST_REMINDED]?.let(Instant::ofEpochMilli)

    override suspend fun markReminded(at: Instant) {
        dataStore.edit { it[LAST_REMINDED] = at.toEpochMilli() }
    }

    override suspend fun isPending() = dataStore.data.first()[RESTORE_PENDING] ?: false

    override suspend fun mark() {
        dataStore.edit { it[RESTORE_PENDING] = true }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(RESTORE_PENDING) }
    }

    companion object {
        const val MIN_DAYS = 1
        const val MAX_DAYS = 365
        private val ENABLED = booleanPreferencesKey("backup.reminder.enabled")
        private val INTERVAL_DAYS = intPreferencesKey("backup.reminder.interval_days")
        private val LAST_REMINDED = longPreferencesKey("backup.reminder.last")
        private val RESTORE_PENDING = booleanPreferencesKey("backup.restore.pending")
    }
}
