package com.al3xiel.my_finances.feature.backup.data

import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderScheduler
import com.al3xiel.my_finances.feature.privacy.domain.model.WipeStep
import javax.inject.Inject

/** Cancela el Worker **antes** de borrar la clave de la base: una corrida a medio borrado abriría una base sin clave. */
class BackupReminderWipeStep @Inject constructor(
    private val scheduler: BackupReminderScheduler,
) : WipeStep {
    override val id = "backup_reminder"
    override val priority = WipeStep.KEY_PRIORITY - 1

    override suspend fun wipe() = scheduler.cancel()
}
