package com.al3xiel.my_finances.feature.expectedincome.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderLog
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interruptor y "ya avisado" en el DataStore de ajustes (no sensible: solo un contador y una fecha por ID). El borrado
 * total limpia ese DataStore entero (`PreferencesWipeStep`).
 */
@Singleton
class ReminderPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReminderRepository {
    override val enabled: Flow<Boolean> = dataStore.data.map { it[ENABLED] ?: false }

    override suspend fun isEnabled() = enabled.first()

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[ENABLED] = enabled }
    }

    override suspend fun log(id: ExpectedIncomeId): ReminderLog {
        val prefs = dataStore.data.first()
        return ReminderLog(
            count = prefs[countKey(id)] ?: 0,
            lastRemindedOn = prefs[lastKey(id)]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        )
    }

    override suspend fun markReminded(ids: Collection<ExpectedIncomeId>, on: LocalDate) {
        dataStore.edit { prefs ->
            ids.forEach { id ->
                prefs[countKey(id)] = (prefs[countKey(id)] ?: 0) + 1
                prefs[lastKey(id)] = on.toString()
            }
        }
    }

    private fun countKey(id: ExpectedIncomeId) = intPreferencesKey("expected_income.reminder.count.${id.value}")

    private fun lastKey(id: ExpectedIncomeId) = stringPreferencesKey("expected_income.reminder.last.${id.value}")

    private companion object {
        val ENABLED = booleanPreferencesKey("expected_income.reminder.enabled")
    }
}
