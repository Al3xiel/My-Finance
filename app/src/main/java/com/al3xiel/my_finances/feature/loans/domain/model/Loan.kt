package com.al3xiel.my_finances.feature.loans.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import java.time.LocalDate

data class Loan(
    val id: LoanId,
    val personId: PersonId,
    val movementId: MovementId,
    val principal: Money,
    val repaid: Money,
    val status: LoanStatus,
    val occurredOn: LocalDate,
) {
    val pending: Money get() = principal - repaid
}
