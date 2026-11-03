package com.al3xiel.my_finances.feature.pools.domain.rules

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.MovementSnapshot
import com.al3xiel.my_finances.domain.usecase.MovementCorrectionGuard
import com.al3xiel.my_finances.domain.usecase.MovementVoidGuard
import com.al3xiel.my_finances.domain.usecase.PoolReferenceGuard
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import javax.inject.Inject

/**
 * Lo que los cúmulos le exigen a un gasto: el cúmulo debe existir y estar activo; un gasto con reembolsos no se anula
 * (D20); y una parte reembolsada no se quita, no cambia de cúmulo ni baja de lo reembolsado.
 */
class PoolExpenseGuard @Inject constructor(
    private val pools: PoolRepository,
) : MovementVoidGuard, MovementCorrectionGuard, PoolReferenceGuard {
    override suspend fun check(movementId: MovementId): BusinessError? =
        if (pools.reimbursedParts(movementId).isEmpty()) null else BusinessError.HasReimbursements

    override suspend fun check(movementId: MovementId, snapshot: MovementSnapshot): BusinessError? {
        if (snapshot !is MovementSnapshot.Expense) return null
        val byPart = snapshot.parts.associateBy { it.partId }
        for (locked in pools.reimbursedParts(movementId)) {
            val part = byPart[locked.partId] ?: return BusinessError.ReimbursedPartLocked
            if (part.poolId != locked.poolId) return BusinessError.ReimbursedPartLocked
            if (part.amount.amountMinor < locked.reimbursed.amountMinor) return BusinessError.BelowReimbursedAmount
        }
        return null
    }

    override suspend fun check(poolId: PoolId, unchanged: Boolean): BusinessError? {
        val pool = pools.find(poolId) ?: return BusinessError.NotFound
        return if (pool.isArchived && !unchanged) BusinessError.AlreadyArchived else null
    }
}
