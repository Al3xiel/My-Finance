package com.al3xiel.my_finances.feature.tags.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.TagId
import com.al3xiel.my_finances.domain.model.event.TagCreated
import com.al3xiel.my_finances.domain.model.event.TagKind
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import com.al3xiel.my_finances.feature.tags.domain.repository.TagRepository
import javax.inject.Inject

class CreateTagUseCase @Inject constructor(
    private val tags: TagRepository,
    private val people: PersonRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    /** Un concepto `PERSON` exige una persona activa; uno `TOPIC` no puede llevarla. */
    suspend operator fun invoke(name: String, kind: TagKind, personId: PersonId?): Outcome<TagId> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(tags.activeNames(), clean)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }
        when (kind) {
            TagKind.PERSON -> {
                val person = personId?.let { people.find(it) }
                if (person == null || person.isArchived) return Outcome.Failed(BusinessError.PersonRequired)
            }
            TagKind.TOPIC -> if (personId != null) return Outcome.Failed(BusinessError.PersonNotAllowed)
        }

        val id = TagId(ids.newId())
        recorder.record(TagCreated(id, clean, kind, personId))
        return Outcome.Ok(id)
    }
}
