package com.al3xiel.my_finances.feature.loans.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.MovementId
import java.time.LocalDate

/** Un pago vigente de la deuda; anularlo es `MovementVoided` sobre su movimiento (D20). */
data class LoanRepaymentLine(
    val movementId: MovementId,
    val amount: Money,
    val occurredOn: LocalDate,
)

data class LoanDetail(
    val loan: Loan,
    val personName: String,
    /** Del más reciente al más antiguo. */
    val repayments: List<LoanRepaymentLine>,
)
