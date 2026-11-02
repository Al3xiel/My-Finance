package com.al3xiel.my_finances.feature.reconciliation.domain

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.feature.reconciliation.domain.model.Reconciliation
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationDetail
import com.al3xiel.my_finances.feature.reconciliation.domain.model.ReconciliationStatus
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeReconciliationRepository : ReconciliationRepository {
    val reconciliations = linkedMapOf<ReconciliationId, Reconciliation>()
    val differences = mutableMapOf<ReconciliationId, Money>()
    val linked = mutableSetOf<Pair<ReconciliationId, MovementId>>()

    /** Lo que emite `observeDetail`; las pruebas lo cambian para simular que la diferencia se mueve en vivo. */
    val details = mutableMapOf<ReconciliationId, MutableStateFlow<ReconciliationDetail?>>()

    override suspend fun find(id: ReconciliationId) = reconciliations[id]

    override suspend fun openFor(accountId: AccountId) =
        reconciliations.values.firstOrNull { it.accountId == accountId && it.status == ReconciliationStatus.OPEN }

    override suspend fun remainingDifference(id: ReconciliationId) = differences[id]

    override suspend fun isLinked(id: ReconciliationId, movementId: MovementId) = (id to movementId) in linked

    override fun observeDetail(id: ReconciliationId): Flow<ReconciliationDetail?> =
        details.getOrPut(id) { MutableStateFlow(null) }

    override fun observeLatest(accountId: AccountId): Flow<Reconciliation?> =
        flowOf(reconciliations.values.lastOrNull { it.accountId == accountId })

    override fun observeNeedsReviewCount(): Flow<Int> = flowOf(reconciliations.values.count { it.needsReview })
}
