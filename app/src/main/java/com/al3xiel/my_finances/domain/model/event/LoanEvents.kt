package com.al3xiel.my_finances.domain.model.event

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.AccountId
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.RepaymentId
import kotlinx.serialization.Serializable

/** Sale dinero; no es gasto. */
@Serializable
data class LoanGranted(
    val loanId: LoanId,
    val movementId: MovementId,
    val personId: PersonId,
    val accountId: AccountId,
    val amount: Money,
    val note: String?,
) : DomainEvent {
    override val type get() = "LoanGranted"
    override val aggregateType get() = AggregateType.LOAN
    override val aggregateId get() = loanId.value
}

/** Entra dinero; no es ingreso. */
@Serializable
data class LoanRepaymentReceived(
    val repaymentId: RepaymentId,
    val movementId: MovementId,
    val loanId: LoanId,
    val accountId: AccountId,
    val amount: Money,
) : DomainEvent {
    override val type get() = "LoanRepaymentReceived"
    override val aggregateType get() = AggregateType.LOAN
    override val aggregateId get() = loanId.value
}
