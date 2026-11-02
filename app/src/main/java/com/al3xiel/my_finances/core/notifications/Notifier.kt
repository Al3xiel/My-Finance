package com.al3xiel.my_finances.core.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.al3xiel.my_finances.MainActivity
import com.al3xiel.my_finances.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica notificaciones locales, privadas y genéricas: el texto sale de `strings.xml` (sin montos, nombres ni datos),
 * la pantalla de bloqueo solo ve una versión pública sin detalle y tocarla abre la app (que pide su propio bloqueo).
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Falso si el usuario negó `POST_NOTIFICATIONS` o apagó las notificaciones de la app: no es un error. */
    fun canNotify(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Devuelve si se publicó. Publicar otra con el mismo [notificationId] reemplaza la anterior. */
    @SuppressLint("MissingPermission") // canNotify() ya lo comprueba.
    fun notifyPrivate(
        channelId: String,
        notificationId: Int,
        @StringRes title: Int,
        @StringRes text: Int,
    ): Boolean {
        if (!canNotify()) return false
        NotificationChannels.ensure(context)
        val publicVersion = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notification_public_text))
            .build()
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        return true
    }

    /** Inmutable: nadie puede reescribir el destino de la notificación. */
    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
