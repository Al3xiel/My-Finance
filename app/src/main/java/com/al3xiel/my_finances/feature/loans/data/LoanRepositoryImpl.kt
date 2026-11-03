package com.al3xiel.my_finances.feature.loans.data

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.data.projection.loan.LoanEntity
import com.al3xiel.my_finances.data.projection.loan.LoanQueryDao
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.MovementId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.feature.loans.domain.model.Loan
import com.al3xiel.my_finances.feature.loans.domain.model.LoanDetail
import com.al3xiel.my_finances.feature.loans.domain.model.LoanRepaymentLine
import com.al3xiel.my_finances.feature.loans.domain.repository.LoanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

class LoanRepositoryImpl @Inject constructor(
    private val queries: LoanQueryDao,
) : LoanRepository {
    override suspend fun find(id: LoanId): Loan? = queries.loan(id.value)?.toDomain()

    override suspend fun findByMovement(movementId: MovementId): Loan? = queries.loanByMovement(movementId.value)?.toDomain()

    override fun observeDetail(id: LoanId): Flow<LoanDetail?> =
        combine(queries.observeLoan(id.value), queries.observeRepayments(id.value)) { row, repayments ->
            row?.let {
                LoanDetail(
                    loan = Loan(
                        LoanId(it.loanId), PersonId(it.personId), MovementId(it.movementId),
                        Money(it.principalMinor, CurrencyCode.PEN), Money(it.repaidMinor, CurrencyCode.PEN), it.status,
                        LocalDate.parse(it.occurredOn),
                    ),
                    personName = it.alias,
                    repayments = repayments.map { r ->
                        LoanRepaymentLine(MovementId(r.movementId), Money(r.amountMinor, CurrencyCode.PEN), LocalDate.parse(r.occurredOn))
                    },
                )
            }
        }
}

private fun LoanEntity.toDomain() = Loan(
    id = LoanId(id),
    personId = PersonId(personId),
    movementId = MovementId(movementId),
    principal = Money(principalMinor, CurrencyCode.PEN),
    repaid = Money(repaidMinor, CurrencyCode.PEN),
    status = status,
    occurredOn = LocalDate.parse(occurredOn),
)
