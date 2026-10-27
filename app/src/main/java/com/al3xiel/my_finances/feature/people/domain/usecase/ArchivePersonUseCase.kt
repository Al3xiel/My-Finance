package com.al3xiel.my_finances.feature.people.domain.usecase

import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.PersonArchived
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import javax.inject.Inject

class ArchivePersonUseCase @Inject constructor(
    private val people: PersonRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: PersonId): Outcome<Unit> {
        val person = people.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (person.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)

        recorder.record(PersonArchived(id))
        return Outcome.Ok(Unit)
    }
}
