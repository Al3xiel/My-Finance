package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import javax.inject.Inject

class RenamePoolUseCase @Inject constructor(
    private val pools: PoolRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: PoolId, name: String): Outcome<Unit> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        val pool = pools.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (pool.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        if (NameNormalizer.isTaken(pools.activeNames(), clean, own = pool.name)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }

        recorder.record(ReimbursementPoolRenamed(id, clean))
        return Outcome.Ok(Unit)
    }
}
