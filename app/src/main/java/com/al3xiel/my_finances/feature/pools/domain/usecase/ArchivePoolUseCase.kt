package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolArchived
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import javax.inject.Inject

/** Un cúmulo con algo pendiente de reembolsar no se archiva: dejaría de aparecer en Por cobrar. */
class ArchivePoolUseCase @Inject constructor(
    private val pools: PoolRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: PoolId): Outcome<Unit> {
        val pool = pools.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (pool.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        if (pools.pendingMinor(id) > 0) return Outcome.Failed(BusinessError.PendingNotZero)

        recorder.record(ReimbursementPoolArchived(id))
        return Outcome.Ok(Unit)
    }
}
