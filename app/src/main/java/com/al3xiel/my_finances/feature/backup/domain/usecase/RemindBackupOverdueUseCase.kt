package com.al3xiel.my_finances.feature.backup.domain.usecase

import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderNotifier
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupSettingsRepository
import com.al3xiel.my_finances.feature.backup.domain.rules.BackupReminderPolicy
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

/** Una corrida del recordatorio. Solo marca "ya avisado" si el aviso se publicó: un permiso negado no lo gasta. */
class RemindBackupOverdueUseCase @Inject constructor(
    private val status: ObserveBackupStatusUseCase,
    private val settings: BackupSettingsRepository,
    private val notifier: BackupReminderNotifier,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Boolean {
        if (!settings.isEnabled()) return false
        val now = clock.instant()
        if (!BackupReminderPolicy.isDue(status().first(), settings.lastRemindedAt(), now)) return false
        if (!notifier.remind()) return false
        settings.markReminded(now)
        return true
    }
}
