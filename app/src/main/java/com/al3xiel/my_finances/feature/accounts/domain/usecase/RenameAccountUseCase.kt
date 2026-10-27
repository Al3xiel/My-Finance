package com.al3xiel.my_finances.feature.accounts.domain.usecase

import com.al3xiel.my_finances.core.text.NameNormalizer
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountRenamed
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

class RenameAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val recorder: EventRecorder,
) {
    suspend operator fun invoke(id: AccountId, name: String): Outcome<Unit> {
        val account = accounts.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (account.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        val clean = name.trim()
        if (clean.isEmpty()) return Outcome.Failed(BusinessError.BlankName)
        if (NameNormalizer.isTaken(accounts.activeNames(), clean, own = account.name)) {
            return Outcome.Failed(BusinessError.DuplicateName)
        }

        recorder.record(AccountRenamed(id, clean))
        return Outcome.Ok(Unit)
    }
}
