package com.al3xiel.my_finances.feature.expectedincome.domain.repository

import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Cuántos avisos lleva un esperado y cuándo fue el último. */
data class ReminderLog(val count: Int, val lastRemindedOn: LocalDate?)

/** Interruptor global y registro de "ya avisado" por esperado; nada sensible (no guarda montos ni nombres). */
interface ReminderRepository {
    val enabled: Flow<Boolean>

    suspend fun isEnabled(): Boolean

    suspend fun setEnabled(enabled: Boolean)

    suspend fun log(id: ExpectedIncomeId): ReminderLog

    suspend fun markReminded(ids: Collection<ExpectedIncomeId>, on: LocalDate)
}

/** Publica un único aviso genérico; devuelve si se publicó (falso si el usuario negó el permiso). */
fun interface ReminderNotifier {
    fun remind(): Boolean
}

/** El Worker periódico del recordatorio. */
interface ReminderScheduler {
    /** Deja programada una sola corrida diaria; repetirlo no duplica. */
    fun ensureScheduled()

    fun cancel()
}
