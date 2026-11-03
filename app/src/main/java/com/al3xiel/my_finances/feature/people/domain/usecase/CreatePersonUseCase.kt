package com.al3xiel.my_finances.feature.people.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.PersonCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import javax.inject.Inject

class CreatePersonUseCase @Inject constructor(
    private val people: PersonRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(alias: String): Outcome<PersonId> {
        val clean = alias.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(people.activeAliases(), clean)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }

        val id = PersonId(ids.newId())
        recorder.record(PersonCreated(id, clean))
        return Outcome.Ok(id)
    }
}
