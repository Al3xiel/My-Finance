package com.al3xiel.my_finances.feature.privacy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.al3xiel.my_finances.feature.privacy.domain.model.SecuritySettings
import com.al3xiel.my_finances.feature.privacy.domain.repository.SecuritySettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreSecuritySettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SecuritySettingsRepository {

    override val settings: Flow<SecuritySettings> = dataStore.data.map { prefs ->
        val defaults = SecuritySettings()
        SecuritySettings(
            lockEnabled = prefs[LOCK_ENABLED] ?: defaults.lockEnabled,
            autoLockMinutes = prefs[AUTO_LOCK_MINUTES] ?: defaults.autoLockMinutes,
            secureScreen = prefs[SECURE_SCREEN] ?: defaults.secureScreen,
            hideAmounts = prefs[HIDE_AMOUNTS] ?: defaults.hideAmounts,
        )
    }

    override suspend fun setLockEnabled(enabled: Boolean) {
        dataStore.edit { it[LOCK_ENABLED] = enabled }
    }

    override suspend fun setAutoLockMinutes(minutes: Int) {
        if (minutes !in SecuritySettings.AUTO_LOCK_OPTIONS) return
        dataStore.edit { it[AUTO_LOCK_MINUTES] = minutes }
    }

    override suspend fun setSecureScreen(enabled: Boolean) {
        dataStore.edit { it[SECURE_SCREEN] = enabled }
    }

    override suspend fun setHideAmounts(enabled: Boolean) {
        dataStore.edit { it[HIDE_AMOUNTS] = enabled }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val LOCK_ENABLED = booleanPreferencesKey("security.lock_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("security.auto_lock_minutes")
        val SECURE_SCREEN = booleanPreferencesKey("security.secure_screen")
        val HIDE_AMOUNTS = booleanPreferencesKey("security.hide_amounts")
    }
}
