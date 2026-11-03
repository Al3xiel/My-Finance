package com.al3xiel.my_finances.feature.categories.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.CategoryArchived
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

class ArchiveCategoryUseCase @Inject constructor(
    private val categories: CategoryRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: CategoryId): Outcome<Unit> {
        val category = categories.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (category.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)

        recorder.record(CategoryArchived(id))
        return Outcome.Ok(Unit)
    }
}
