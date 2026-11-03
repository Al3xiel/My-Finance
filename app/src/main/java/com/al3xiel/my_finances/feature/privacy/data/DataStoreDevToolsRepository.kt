package com.al3xiel.my_finances.feature.privacy.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.al3xiel.my_finances.feature.privacy.domain.repository.DevToolsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DataStoreDevToolsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) : DevToolsRepository {
    override val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("devtools.enabled")
    }
}
