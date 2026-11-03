package com.al3xiel.my_finances.feature.accounts.domain.usecase

import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.event.AccountArchived
import com.al3xiel.my_finances.domain.usecase.AccountArchiveGuard
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import javax.inject.Inject

class ArchiveAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val recorder: EventRecorder,
    private val guards: Set<@JvmSuppressWildcards AccountArchiveGuard> = emptySet(),
) {
    suspend operator fun invoke(id: AccountId): Outcome<Unit> {
        val account = accounts.find(id) ?: return Outcome.Failed(BusinessError.NotFound)
        if (account.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        if (accounts.balanceMinor(id) != 0L) return Outcome.Failed(BusinessError.BalanceNotZero)
        guards.firstNotNullOfOrNull { it.check(id) }?.let { return Outcome.Failed(it) }

        recorder.record(AccountArchived(id))
        return Outcome.Ok(Unit)
    }
}
