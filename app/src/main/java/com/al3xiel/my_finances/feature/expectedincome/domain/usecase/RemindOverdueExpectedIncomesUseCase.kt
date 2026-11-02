package com.al3xiel.my_finances.feature.expectedincome.domain.usecase

import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ExpectedIncomeRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderNotifier
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderRepository
import com.al3xiel.my_finances.feature.expectedincome.domain.rules.ReminderPolicy
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Una corrida del recordatorio: con el interruptor encendido, junta los esperados vencidos a los que les toca aviso
 * (`ReminderPolicy`) y publica **un solo** aviso agrupado. Solo marca "ya avisado" si el aviso se publicó, así un
 * permiso negado no gasta los 3 avisos de cada esperado. Devuelve cuántos esperados cubrió el aviso.
 */
class RemindOverdueExpectedIncomesUseCase @Inject constructor(
    private val expectedIncomes: ExpectedIncomeRepository,
    private val reminders: ReminderRepository,
    private val notifier: ReminderNotifier,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Int {
        if (!reminders.isEnabled()) return 0
        val today = LocalDate.now(clock)
        val due = expectedIncomes.overdue().filter {
            val log = reminders.log(it.id)
            ReminderPolicy.isDue(it.range.to, log.count, log.lastRemindedOn, today)
        }
        if (due.isEmpty() || !notifier.remind()) return 0

        reminders.markReminded(due.map { it.id }, today)
        return due.size
    }
}
