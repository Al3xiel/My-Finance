package com.al3xiel.my_finances.feature.people.domain.model

import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PartId
import com.al3xiel.my_finances.feature.loans.domain.model.LoanStatus
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import java.time.LocalDate

data class ReimbursementLine(
    val movementId: MovementId,
    val amount: Money,
    val occurredOn: LocalDate,
    val isVoided: Boolean,
)

/** Un gasto de los cúmulos de la persona, con su concepto y lo que falta reembolsar. */
data class PersonPartLine(
    val partId: PartId,
    val poolName: String,
    val occurredOn: LocalDate,
    val categoryName: String,
    val tagNames: List<String>,
    val amount: Money,
    val pending: Money,
)

data class PersonLoanLine(
    val loan: LoanReceivable,
    val status: LoanStatus,
)

data class PersonDetail(
    val person: Person,
    /** Pendiente de sus cúmulos activos más sus deudas abiertas. */
    val balance: Money,
    val pools: List<PoolReceivable>,
    val loans: List<PersonLoanLine>,
    val reimbursements: List<ReimbursementLine>,
    val parts: List<PersonPartLine>,
)
