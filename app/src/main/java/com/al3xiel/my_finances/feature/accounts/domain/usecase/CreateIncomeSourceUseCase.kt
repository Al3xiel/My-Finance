package com.al3xiel.my_finances.feature.accounts.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.IncomeSourceId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.IncomeSourceCreated
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.IncomeSourceRepository
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import javax.inject.Inject

class CreateIncomeSourceUseCase @Inject constructor(
    private val sources: IncomeSourceRepository,
    private val people: PersonRepository,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(name: String, personId: PersonId?): Outcome<IncomeSourceId> {
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(sources.activeNames(), clean)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }
        if (personId != null && people.find(personId) == null) return Outcome.Failed(BusinessError.NotFound)

        val id = IncomeSourceId(ids.newId())
        recorder.record(IncomeSourceCreated(id, clean, personId))
        return Outcome.Ok(id)
    }
}
