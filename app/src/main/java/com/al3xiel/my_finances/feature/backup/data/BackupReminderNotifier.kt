package com.al3xiel.my_finances.feature.backup.data

import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.notifications.NotificationChannels
import com.al3xiel.my_finances.core.notifications.Notifier
import com.al3xiel.my_finances.feature.backup.domain.repository.BackupReminderNotifier
import javax.inject.Inject

/** Aviso genérico (sin montos ni nombres) con ID propio, así uno nuevo reemplaza al anterior. */
class AndroidBackupReminderNotifier @Inject constructor(
    private val notifier: Notifier,
) : BackupReminderNotifier {
    override fun remind(): Boolean =
        notifier.notifyPrivate(NotificationChannels.REMINDERS, NOTIFICATION_ID, R.string.backup_reminder_title, R.string.backup_reminder_text)

    private companion object {
        const val NOTIFICATION_ID = 1002
    }
}
