package com.al3xiel.my_finances.feature.tags.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.TagArchived
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import javax.inject.Inject

class ArchiveTagUseCase @Inject constructor(
    private val tags: TagRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: TagId): Outcome<Unit> {
        val tag = tags.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (tag.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)

        recorder.record(TagArchived(id))
        return Outcome.Ok(Unit)
    }
}
