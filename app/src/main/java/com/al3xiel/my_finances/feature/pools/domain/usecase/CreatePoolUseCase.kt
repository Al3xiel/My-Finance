package com.al3xiel.my_finances.feature.pools.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.CategoryId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.PoolKind
import com.al3xiel.my_finances.domain.model.event.ReimbursementPoolCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.categories.domain.repository.CategoryRepository
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.pools.domain.repository.PoolRepository
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import javax.inject.Inject

/** Un cúmulo siempre tiene responsable: es quien reembolsa. Categoría y conceptos por defecto son opcionales. */
class CreatePoolUseCase @Inject constructor(
    private val pools: PoolRepository,
    private val people: PersonRepository,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(
        name: String,
        responsiblePersonId: PersonId?,
        kind: PoolKind,
        defaultCategoryId: CategoryId?,
        defaultTagIds: List<TagId>,
    ): Outcome<PoolId> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (responsiblePersonId == null) return Outcome.Failed(BusinessError.PersonRequired)
        val person = people.find(responsiblePersonId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (person.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        if (NameNormalizer.isTaken(pools.activeNames(), clean)) return Outcome.Failed(BusinessError.DuplicateName)

        if (defaultCategoryId != null) {
            val category = categories.find(defaultCategoryId) ?: return Outcome.Failed(BusinessError.NotFound)
            if (category.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        }
        val tagIds = defaultTagIds.distinct()
        for (tagId in tagIds) {
            val tag = tags.find(tagId) ?: return Outcome.Failed(BusinessError.NotFound)
            if (tag.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        }

        val id = PoolId(ids.newId())
        recorder.record(ReimbursementPoolCreated(id, clean, responsiblePersonId, kind, defaultCategoryId, tagIds))
        return Outcome.Ok(id)
    }
}
