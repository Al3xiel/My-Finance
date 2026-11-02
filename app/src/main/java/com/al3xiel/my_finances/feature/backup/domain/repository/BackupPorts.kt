package com.al3xiel.my_finances.feature.backup.domain.repository

import java.io.IOException

/** Cambia el archivo de la base por el de staging ya verificado. Si falla, la base original queda como estaba. */
fun interface DatabaseSwapper {
    @Throws(IOException::class)
    suspend fun swap()
}

/** Reinicia el proceso para que Room abra el archivo nuevo. */
fun interface AppRestarter {
    fun restart()
}

/** Publica un único aviso genérico de respaldo vencido; devuelve si se publicó (falso si no hay permiso). */
fun interface BackupReminderNotifier {
    fun remind(): Boolean
}

/** El Worker periódico del recordatorio de respaldo. */
interface BackupReminderScheduler {
    /** Deja programada una sola corrida diaria; repetirlo no duplica. */
    fun ensureScheduled()

    fun cancel()
}
