package com.al3xiel.my_finances.feature.loans.domain.usecase

import com.al3xiel.my_finances.core.id.IdGenerator
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.BusinessError
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.Outcome
import com.al3xiel.my_finances.domain.model.RepaymentId
import com.al3xiel.my_finances.domain.model.event.LoanRepaymentReceived
import com.al3xiel.my_finances.domain.usecase.EventRecorder
import com.al3xiel.my_finances.feature.loans.domain.repository.LoanRepository
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementRules
import com.al3xiel.my_finances.feature.movements.domain.rules.MovementWriteLock
import java.time.LocalDate
import javax.inject.Inject

/** Entra dinero pero no es ingreso. Un pago mayor a lo pendiente se rechaza (D1). Anularlo es `MovementVoided` (D20). */
class ReceiveLoanRepaymentUseCase @Inject constructor(
    private val loans: LoanRepository,
    private val rules: MovementRules,
    private val recorder: EventRecorder,
    private val ids: IdGenerator,
    private val lock: MovementWriteLock,
) {
    suspend operator fun invoke(loanId: LoanId, accountId: AccountId, amount: Money, occurredOn: LocalDate): Outcome<MovementId> =
        lock.withLock { execute(loanId, accountId, amount, occurredOn) }

    private suspend fun execute(loanId: LoanId, accountId: AccountId, amount: Money, occurredOn: LocalDate): Outcome<MovementId> {
        rules.checkMoney(amount)?.let { return Outcome.Failed(it) }
        rules.checkDate(occurredOn)?.let { return Outcome.Failed(it) }
        rules.checkAccount(accountId)?.let { return Outcome.Failed(it) }
        val loan = loans.find(loanId) ?: return Outcome.Failed(BusinessError.NotFound)
        if (amount.amountMinor > loan.pending.amountMinor) return Outcome.Failed(BusinessError.ExceedsPending)

        val repaymentId = RepaymentId(ids.newId())
        val movementId = MovementId(ids.newId())
        recorder.record(LoanRepaymentReceived(repaymentId, movementId, loanId, accountId, amount), occurredOn)
        return Outcome.Ok(movementId)
    }
}
