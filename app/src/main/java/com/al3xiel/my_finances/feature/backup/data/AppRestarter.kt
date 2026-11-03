package com.al3xiel.my_finances.feature.backup.data

import android.content.Context
import android.content.Intent
import com.al3xiel.my_finances.feature.backup.domain.repository.AppRestarter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.system.exitProcess

/** Abre la app de nuevo y termina el proceso: Room vuelve a abrir el archivo que se acaba de intercambiar. */
class ProcessAppRestarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppRestarter {
    override fun restart() {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(intent)
        }
        exitProcess(0)
    }
}
