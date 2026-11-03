package com.al3xiel.my_finances.feature.expectedincome.data

import com.al3xiel.my_finances.R
import com.al3xiel.my_finances.core.notifications.NotificationChannels
import com.al3xiel.my_finances.core.notifications.Notifier
import com.al3xiel.my_finances.feature.expectedincome.domain.repository.ReminderNotifier
import javax.inject.Inject

/** El aviso de ingresos vencidos: genérico (sin montos ni nombres) y con un solo ID, así un aviso nuevo reemplaza al anterior. */
class ExpectedIncomeReminderNotifier @Inject constructor(
    private val notifier: Notifier,
) : ReminderNotifier {
    override fun remind() =
        notifier.notifyPrivate(NotificationChannels.REMINDERS, NOTIFICATION_ID, R.string.reminder_overdue_title, R.string.reminder_overdue_text)

    private companion object {
        const val NOTIFICATION_ID = 1001
    }
}
