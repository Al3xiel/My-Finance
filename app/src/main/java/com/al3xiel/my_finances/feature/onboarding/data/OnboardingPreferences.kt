package com.al3xiel.my_finances.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.al3xiel.my_finances.feature.onboarding.domain.repository.OnboardingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Vive en el DataStore de ajustes, así que `PreferencesWipeStep` ya la borra con «Borrar todos mis datos». */
@Singleton
class OnboardingPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) : OnboardingRepository {
    override val completed: Flow<Boolean> = dataStore.data.map { it[COMPLETED] ?: false }

    override suspend fun markCompleted() {
        dataStore.edit { it[COMPLETED] = true }
    }

    private companion object {
        val COMPLETED = booleanPreferencesKey("onboarding.completed")
    }
}
