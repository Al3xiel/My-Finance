package com.al3xiel.my_finances.feature.categories.domain.usecase

import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.CategoryRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

class RenameCategoryUseCase @Inject constructor(
    private val categories: CategoryRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: CategoryId, name: String): Outcome<Unit> {
        val category = categories.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (category.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(categories.activeNames(), clean, own = category.name)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }

        recorder.record(CategoryRenamed(id, clean))
        return Outcome.Ok(Unit)
    }
}
