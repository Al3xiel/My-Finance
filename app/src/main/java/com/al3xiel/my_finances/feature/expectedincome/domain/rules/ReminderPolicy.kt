package com.al3xiel.my_finances.feature.expectedincome.domain.rules

import java.time.LocalDate

/**
 * Cuándo avisar de un esperado vencido sin confirmar: el día siguiente a `expectedTo`, luego cada 3 días, máximo 3
 * avisos por esperado (docs/02 §8). El llamador solo pregunta por esperados pendientes.
 */
object ReminderPolicy {
    const val MAX_REMINDERS = 3
    const val INTERVAL_DAYS = 3L

    fun isDue(expectedTo: LocalDate, remindersSent: Int, lastRemindedOn: LocalDate?, today: LocalDate): Boolean = when {
        remindersSent >= MAX_REMINDERS -> false
        lastRemindedOn == null -> !today.isBefore(expectedTo.plusDays(1))
        else -> !today.isBefore(lastRemindedOn.plusDays(INTERVAL_DAYS))
    }
}
