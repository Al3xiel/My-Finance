package com.al3xiel.my_finances.feature.expectedincome.data

import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderScheduler
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import javax.inject.Inject

/**
 * Cancela el Worker **antes** de borrar la clave de la base: si no, una corrida a medio borrado abriría una base sin
 * clave. El "ya avisado" lo limpia `PreferencesWipeStep`, que borra el DataStore entero.
 */
class ReminderWipeStep @Inject constructor(
    private val scheduler: ReminderScheduler,
) : WipeStep {
    override val id = "expected_income_reminders"
    override val priority = WipeStep.KEY_PRIORITY - 1

    override suspend fun wipe() = scheduler.cancel()
}
