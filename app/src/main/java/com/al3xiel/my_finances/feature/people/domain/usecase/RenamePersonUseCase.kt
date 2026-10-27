package com.al3xiel.my_finances.feature.people.domain.usecase

import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.PersonRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import javax.inject.Inject

class RenamePersonUseCase @Inject constructor(
    private val people: PersonRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: PersonId, alias: String): Outcome<Unit> {
        val person = people.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (person.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        val clean = alias.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(people.activeAliases(), clean, own = person.alias)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }

        recorder.record(PersonRenamed(id, clean))
        return Outcome.Ok(Unit)
    }
}
