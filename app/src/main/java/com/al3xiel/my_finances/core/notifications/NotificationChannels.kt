package com.al3xiel.my_finances.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.al3xiel.my_finances.R

/**
 * Canales de notificación de la app. Todos son privados en la pantalla de bloqueo: nunca muestran el contenido hasta que
 * el teléfono se desbloquea (docs/02 §8).
 */
object NotificationChannels {
    /** Recordatorios útiles y configurables: ingreso esperado vencido, y luego respaldo y cobros pendientes. */
    const val REMINDERS = "reminders"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            REMINDERS,
            context.getString(R.string.notification_channel_reminders_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_description)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
