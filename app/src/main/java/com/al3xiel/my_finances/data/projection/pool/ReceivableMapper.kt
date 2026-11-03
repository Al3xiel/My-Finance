package com.al3xiel.my_finances.data.projection.pool

import com.al3xiel.my_finances.core.money.CurrencyCode
import com.al3xiel.my_finances.core.money.Money
import com.al3xiel.my_finances.domain.model.LoanId
import com.al3xiel.my_finances.domain.model.PersonId
import com.al3xiel.my_finances.domain.model.PoolId
import com.al3xiel.my_finances.feature.pools.domain.model.LoanReceivable
import com.al3xiel.my_finances.feature.pools.domain.model.PoolReceivable
import java.time.LocalDate

private fun pen(minor: Long) = Money(minor, CurrencyCode.PEN)

/** Filas de las consultas de Por cobrar a sus modelos; las usan Por cobrar y el detalle de persona. */
fun PoolPendingRow.toReceivable() = PoolReceivable(
    poolId = PoolId(poolId),
    name = name,
    kind = kind,
    responsiblePersonId = PersonId(personId),
    responsibleAlias = alias,
    periodPending = pen(periodPending),
    totalPending = pen(totalPending),
)

fun OpenLoanRow.toReceivable() = LoanReceivable(
    loanId = LoanId(loanId),
    personId = PersonId(personId),
    alias = alias,
    principal = pen(principalMinor),
    repaid = pen(repaidMinor),
    occurredOn = LocalDate.parse(occurredOn),
)
