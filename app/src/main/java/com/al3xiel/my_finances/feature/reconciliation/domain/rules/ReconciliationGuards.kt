package com.al3xiel.my_finances.feature.reconciliation.domain.rules

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.ReconciliationId
import com.al3xiel.my_finances.domain.usecase.AccountArchiveGuard
import com.al3xiel.my_finances.domain.usecase.ReconciliationLinkGuard
import com.al3xiel.my_finances.feature.reconciliation.domain.repository.ReconciliationRepository
import javax.inject.Inject

/** Lo que la conciliación les exige a los casos de uso de otras features (D34, P6). */
class ReconciliationGuards @Inject constructor(
    private val reconciliations: ReconciliationRepository,
) : ReconciliationLinkGuard, AccountArchiveGuard {
    override suspend fun check(reconciliationId: ReconciliationId, accountIds: Set<AccountId>): BusinessError? =
        ReconciliationRules.linkError(reconciliations.find(reconciliationId), accountIds)

    override suspend fun check(accountId: AccountId): BusinessError? =
        if (reconciliations.openFor(accountId) != null) BusinessError.HasOpenReconciliation else null
}
