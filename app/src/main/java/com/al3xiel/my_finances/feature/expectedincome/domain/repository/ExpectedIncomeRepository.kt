package com.al3xiel.my_finances.feature.expectedincome.domain.repository

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.ExpectedIncomeId
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.feature.expectedincome.domain.model.ExpectedIncome
import kotlinx.coroutines.flow.Flow

/** Lecturas con el estado efectivo ya calculado contra hoy (D27). Pendiente = programado o vencido. */
interface ExpectedIncomeRepository {
    suspend fun find(id: ExpectedIncomeId): ExpectedIncome?

    /** Todos, del rango más próximo al más lejano; la pantalla los agrupa por estado. */
    fun observeAll(): Flow<List<ExpectedIncome>>

    /** Los pendientes de esa cuenta y fuente, para sugerirlos al registrar un ingreso (S23). */
    suspend fun pendingFor(accountId: AccountId, incomeSourceId: IncomeSourceId): List<ExpectedIncome>

    /** Pendientes cuyo rango ya terminó, del más antiguo al más reciente (alerta de Inicio y recordatorios). */
    suspend fun overdue(): List<ExpectedIncome>

    /** El pendiente programado más próximo que aún no venció (Inicio). */
    suspend fun next(): ExpectedIncome?

    /** El esperado que confirmó ese ingreso, mientras el ingreso siga activo (anulado, el esperado vuelve a ser pendiente). */
    suspend fun confirmedBy(movementId: MovementId): ExpectedIncome?
}
