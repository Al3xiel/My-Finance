package com.al3xiel.my_finances.feature.reconciliation.domain.repository

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import kotlinx.coroutines.flow.Flow

interface ReconciliationRepository {
    suspend fun find(id: ReconciliationId): Reconciliation?

    /** La conciliación `OPEN` de la cuenta, si hay (una sola por cuenta). */
    suspend fun openFor(accountId: AccountId): Reconciliation?

    /** Diferencia restante ahora (D30): real − saldo de la app al momento; `null` si la conciliación no existe. */
    suspend fun remainingDifference(id: ReconciliationId): Money?

    suspend fun isLinked(id: ReconciliationId, movementId: MovementId): Boolean

    fun observeDetail(id: ReconciliationId): Flow<ReconciliationDetail?>

    /** La más reciente de la cuenta, de cualquier estado. */
    fun observeLatest(accountId: AccountId): Flow<Reconciliation?>

    /** Cuántas resueltas piden revisión, para el aviso de inicio. */
    fun observeNeedsReviewCount(): Flow<Int>

    /** La que pide revisión desde hace más tiempo, o `null` (alerta de Inicio). */
    fun observeOldestNeedsReviewId(): Flow<ReconciliationId?>

    /** La `OPEN` más antigua, o `null` (alerta de Inicio). */
    fun observeOpenId(): Flow<ReconciliationId?>
}
