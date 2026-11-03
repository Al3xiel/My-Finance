package com.al3xiel.my_finances.feature.categories.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.CategoryCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import javax.inject.Inject

class CreateCategoryUseCase @Inject constructor(
    private val categories: CategoryRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    /** Las categorías tienen un solo nivel: el padre debe existir, estar activo y no tener padre. */
    suspend operator fun invoke(name: String, icon: String, color: String, parentId: CategoryId?): Outcome<CategoryId> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(categories.activeNames(), clean)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }
        if (parentId != null) {
            val parent = categories.find(parentId)
            if (parent == null || parent.isArchived || parent.parentId != null) return Outcome.Failed(BusinessError.InvalidParent)
        }

        val id = CategoryId(ids.newId())
        recorder.record(CategoryCreated(id, clean, icon, color, parentId))
        return Outcome.Ok(id)
    }
}
