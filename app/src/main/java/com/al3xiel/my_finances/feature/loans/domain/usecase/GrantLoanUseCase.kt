package com.al3xiel.my_finances.feature.loans.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.event.LoanGranted
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.accounts.domain.repository.AccountRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import com.al3xiel.my_finances.feature.people.domain.repository.PersonRepository
import java.time.LocalDate
import javax.inject.Inject

/** Presta dinero a una persona: sale de la cuenta pero no es un gasto. Anular el préstamo es `MovementVoided` (D20). */
class GrantLoanUseCase @Inject constructor(
    private val people: PersonRepository,
    private val accounts: AccountRepository,
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    /** Si el saldo queda negativo avisa con `WouldGoNegative`; repetir con `confirmNegativeBalance` lo guarda (D14). */
    suspend operator fun invoke(
        personId: PersonId,
        accountId: AccountId,
        amount: Money,
        note: String?,
        occurredOn: LocalDate,
        confirmNegativeBalance: Boolean = false,
    ): Outcome<LoanId> =
        lock.withLock { execute(personId, accountId, amount, note, occurredOn, confirmNegativeBalance) }

    private suspend fun execute(
        personId: PersonId,
        accountId: AccountId,
        amount: Money,
        note: String?,
        occurredOn: LocalDate,
        confirmNegativeBalance: Boolean,
    ): Outcome<LoanId> {
        rules.checkMoney(amount)?.let { return Outcome.Failed(it) }
        rules.checkDate(occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(accountId)?.let { return Outcome.Failed(it) }
        val person = people.find(personId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (person.isArchived) return Outcome.Failed(BusinessError.AlreadyArchived)
        if (!confirmNegativeBalance) {
            val balance = accounts.balanceMinor(accountId)
            rules.negativeBalanceWarning(balance, balance - amount.amountMinor)?.let { return Outcome.Failed(it) }
        }

        val loanId = LoanId(ids.newId())
        recorder.record(
            LoanGranted(loanId, MovementId(ids.newId()), personId, accountId, amount, note?.trim()?.ifEmpty { null }),
            occurredOn,
        )
        return Outcome.Ok(loanId)
    }
}
